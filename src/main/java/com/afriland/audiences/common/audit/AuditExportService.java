package com.afriland.audiences.common.audit;

import com.afriland.audiences.common.audit.AuditExportRenderer.LigneExport;
import com.afriland.audiences.common.audit.HistoriqueActionSpecifications.Perimetre;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.ged.service.MinioStorageService;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Extraction de l'audit vers un fichier scellé (Q-113).
 *
 * <p><b>Ce que l'export ajoute à l'écran.</b> Un inspecteur ne consulte pas un écran : il emporte
 * une pièce. Mais une pièce qu'on peut rééditer ne prouve rien — d'où la chaîne complète : produire,
 * calculer une empreinte, déposer dans MinIO, consigner l'empreinte en base. Le fichier remis et le
 * fichier scellé sont alors démontrablement le même.
 *
 * <p><b>Le plafond n'est pas une limite technique.</b> Au-delà de {@value #PLAFOND_LIGNES} lignes,
 * un PDF cesse d'être un document et un tableur devient un déversement : on refuse, en disant quoi
 * faire — restreindre la période. Tronquer silencieusement aurait produit une archive qui ment sur
 * ce qu'elle couvre, et dont l'empreinte scellerait ce mensonge.
 */
@Service
@RequiredArgsConstructor
public class AuditExportService {

    /**
     * Au-delà, l'extraction est refusée. Choisi pour que le fichier reste consultable par un
     * humain : c'est un critère d'usage, pas de mémoire.
     */
    public static final int PLAFOND_LIGNES = 20_000;

    private static final Sort TRI = Sort.by(Sort.Direction.DESC, "dateAction").and(Sort.by(Sort.Direction.DESC, "id"));

    private final HistoriqueActionRepository historiqueActionRepository;
    private final ExportAuditRepository exportAuditRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final HistoriqueActionService historiqueActionService;
    private final AuditExportRenderer renderer;
    private final MinioStorageService minioStorageService;
    private final CurrentUserService currentUserService;

    /** Le fichier produit, et la ligne qui le scelle. */
    public record Extraction(byte[] contenu, ExportAudit archive, String nomFichier) {
    }

    @Transactional
    public Extraction exporter(Perimetre perimetre, Long dossierId, Long utilisateurId,
                                  TypeActionHistorique code, LocalDate dateDebut, LocalDate dateFin,
                                  FormatExportAudit format) {
        if (dateDebut == null || dateFin == null) {
            // Une archive sans période ne dit pas ce qu'elle contient : son empreinte ne prouverait
            // que son intégrité, jamais son exhaustivité.
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Une extraction exige une période : indiquez une date de début et une date de fin");
        }
        if (dateDebut.isAfter(dateFin)) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "La date de début ne peut pas être postérieure à la date de fin");
        }
        if (perimetre == Perimetre.UN_DOSSIER && dossierId == null) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Le périmètre « un dossier » exige l'identifiant du dossier");
        }

        var specification = HistoriqueActionSpecifications.withFilters(
                perimetre, dossierId, utilisateurId, code, dateDebut, dateFin);

        long total = historiqueActionRepository.count(specification);
        if (total > PLAFOND_LIGNES) {
            throw AuditExportRenderer.extractionTropVaste(PLAFOND_LIGNES);
        }

        List<HistoriqueAction> entrees = historiqueActionRepository.findAll(specification, TRI);
        List<LigneExport> lignes = mettreEnLignes(entrees);

        String periode = "du " + dateDebut + " au " + dateFin;
        byte[] contenu = format == FormatExportAudit.PDF
                ? renderer.pdf(lignes, dateDebut, dateFin, periode)
                : renderer.excel(lignes, dateDebut, dateFin);

        String empreinte = empreinte(contenu);
        String nomFichier = "audit-" + dateDebut + "-" + dateFin + "." + format.extension();
        String cle = minioStorageService.deposerArchiveAudit(nomFichier, format.typeMime(), contenu);

        Utilisateur auteur = currentUserService.getCurrentUser();
        ExportAudit archive = exportAuditRepository.save(ExportAudit.builder()
                .utilisateur(auteur)
                .dateDebut(dateDebut).dateFin(dateFin)
                .perimetre(perimetre).format(format)
                .lignes(lignes.size())
                .empreinte(empreinte)
                .tailleOctets(contenu.length)
                .cleStockage(cle)
                .build());

        historiqueActionService.enregistrer(null, TypeActionHistorique.AUDIT_EXPORTE, criteres(
                perimetre, dossierId, utilisateurId, code, dateDebut, dateFin, format, lignes.size(), empreinte));

        return new Extraction(contenu, archive, nomFichier);
    }

    /**
     * URL de récupération d'une archive déjà produite, <b>et la trace de cette récupération</b>.
     *
     * <p>Sans cette trace, l'archive serait un trou plus large que la table qu'elle double : on
     * pourrait y puiser indéfiniment sans que rien ne le dise.
     */
    @Transactional
    public String telecharger(Long exportId) {
        ExportAudit archive = exportAuditRepository.findById(exportId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Archive d'audit introuvable : " + exportId));

        historiqueActionService.enregistrer(null, TypeActionHistorique.ARCHIVE_TELECHARGEE,
                Map.of("exportId", archive.getId(), "empreinte", archive.getEmpreinte()));

        return minioStorageService.urlTelechargement(archive.getCleStockage());
    }

    /**
     * SHA-256 des octets <b>déposés</b>, pas des données interrogées : c'est le fichier qu'on
     * pourra vérifier plus tard, pas la requête qui l'a produit.
     */
    static String empreinte(byte[] contenu) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est exigé de toute implémentation Java : inatteignable en pratique.
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private List<LigneExport> mettreEnLignes(List<HistoriqueAction> entrees) {
        /*
         * Les noms d'auteur sont résolus en **une** requête, pas une par ligne : sur vingt mille
         * entrées, la version naïve aurait produit vingt mille allers-retours.
         */
        Map<Long, String> noms = utilisateurRepository.findAll().stream()
                .collect(Collectors.toMap(Utilisateur::getId, AuditExportService::nomComplet, (a, b) -> a));

        return entrees.stream()
                .map(e -> new LigneExport(
                        AuditExportRenderer.horodater(e.getDateAction()),
                        e.getUtilisateur() == null ? "" : noms.getOrDefault(e.getUtilisateur().getId(), ""),
                        e.getDossierId() == null ? "" : String.valueOf(e.getDossierId()),
                        e.getCode() == null ? e.getAction() : e.getCode().libelle(),
                        AuditExportRenderer.aplatir(e.getDetails())))
                .toList();
    }

    private static String nomComplet(Utilisateur utilisateur) {
        String prenom = utilisateur.getPrenom() == null ? "" : utilisateur.getPrenom();
        String nom = utilisateur.getNom() == null ? "" : utilisateur.getNom();
        String complet = (prenom + " " + nom).trim();
        return complet.isEmpty() ? String.valueOf(utilisateur.getId()) : complet;
    }

    private Map<String, Object> criteres(Perimetre perimetre, Long dossierId, Long utilisateurId,
                                            TypeActionHistorique code, LocalDate dateDebut, LocalDate dateFin,
                                            FormatExportAudit format, int lignes, String empreinte) {
        Map<String, Object> criteres = new LinkedHashMap<>();
        criteres.put("perimetre", perimetre.name());
        criteres.put("format", format.name());
        criteres.put("dateDebut", dateDebut.toString());
        criteres.put("dateFin", dateFin.toString());
        criteres.put("lignes", lignes);
        criteres.put("empreinte", empreinte);
        if (dossierId != null) criteres.put("dossierId", dossierId);
        if (utilisateurId != null) criteres.put("auteurRecherche", utilisateurId);
        if (code != null) criteres.put("codeRecherche", code.name());
        return criteres;
    }

    /** Conversion d'une archive vers sa forme d'API, sans exposer la clé de stockage. */
    static Function<ExportAudit, ExportAuditResponse> versReponse() {
        return archive -> new ExportAuditResponse(
                archive.getId(),
                archive.getUtilisateur() == null ? null : archive.getUtilisateur().getId(),
                archive.getDateExport(), archive.getDateDebut(), archive.getDateFin(),
                archive.getPerimetre(), archive.getFormat(), archive.getLignes(),
                archive.getEmpreinte(), archive.getTailleOctets());
    }
}
