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

        /*
         * La référence est lue **avant** la production du fichier, pour pouvoir y figurer. C'est la
         * seule chose qui identifie l'archive et que le fichier puisse porter : tout ce qui décrit
         * son contenu — l'empreinte au premier chef — est par construction impossible à écrire
         * dedans sans le changer.
         */
        String reference = String.format("AUD-%d-%04d",
                LocalDate.now().getYear(), exportAuditRepository.prochainNumeroReference());
        String titre = "Audit " + reference + " - du " + dateDebut + " au " + dateFin;

        Utilisateur auteur = currentUserService.getCurrentUser();
        byte[] contenu = format == FormatExportAudit.PDF
                ? renderer.pdf(lignes, dateDebut, dateFin, titre, nomComplet(auteur))
                : renderer.excel(lignes, dateDebut, dateFin, titre, nomComplet(auteur));

        String empreinte = empreinte(contenu);
        String nomFichier = reference.toLowerCase() + "-" + dateDebut + "-" + dateFin + "." + format.extension();
        String cle = minioStorageService.deposerArchiveAudit(nomFichier, format.typeMime(), contenu);

        ExportAudit archive = exportAuditRepository.save(ExportAudit.builder()
                .utilisateur(auteur)
                .dateDebut(dateDebut).dateFin(dateFin)
                .perimetre(perimetre).format(format)
                .lignes(lignes.size())
                .empreinte(empreinte)
                .tailleOctets(contenu.length)
                .cleStockage(cle)
                .reference(reference)
                .build());

        historiqueActionService.enregistrer(null, TypeActionHistorique.AUDIT_EXPORTE, criteres(
                perimetre, dossierId, utilisateurId, code, dateDebut, dateFin, format, lignes.size(), empreinte,
                reference));

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
     * Vérifie qu'un fichier détenu par quelqu'un correspond à une archive connue (Q-114).
     *
     * <p><b>C'est ce qui rend l'empreinte utile.</b> Sans ce chemin, la vérification supposait
     * d'ouvrir un terminal, de taper {@code sha256sum} et de comparer soixante-quatre caractères à
     * l'œil — autant dire que personne ne la faisait. L'empreinte est calculée par le navigateur ;
     * seul le résultat arrive ici, le fichier ne quitte jamais le poste.
     *
     * <p><b>Cette consultation n'est pas historisée</b>, et c'est délibéré : elle ne révèle aucun
     * contenu, et une empreinte ne s'énumère pas — il faut déjà détenir le fichier pour interroger.
     * La tracer n'aurait ajouté que du bruit à ce qu'on cherche à voir.
     */
    @Transactional(readOnly = true)
    public VerificationResponse verifier(String empreinte) {
        String normalisee = empreinte == null ? "" : empreinte.trim().toLowerCase();
        if (!normalisee.matches("[0-9a-f]{64}")) {
            throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Une empreinte SHA-256 compte 64 caractères hexadécimaux");
        }
        return exportAuditRepository.findByEmpreinte(normalisee)
                .map(archive -> new VerificationResponse(true, versReponse().apply(archive)))
                // Un fichier inconnu n'est pas une erreur : c'est une réponse, et souvent celle
                // qu'on redoutait. Un 404 l'aurait fait passer pour une panne.
                .orElseGet(() -> new VerificationResponse(false, null));
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
                                            FormatExportAudit format, int lignes, String empreinte,
                                            String reference) {
        Map<String, Object> criteres = new LinkedHashMap<>();
        criteres.put("reference", reference);
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
                archive.getReference(),
                archive.getUtilisateur() == null ? null : archive.getUtilisateur().getId(),
                archive.getDateExport(), archive.getDateDebut(), archive.getDateFin(),
                archive.getPerimetre(), archive.getFormat(), archive.getLignes(),
                archive.getEmpreinte(), archive.getTailleOctets());
    }
}
