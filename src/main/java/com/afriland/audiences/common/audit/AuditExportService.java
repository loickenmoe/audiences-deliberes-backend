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
 * Exports the audit to a sealed file: generate, hash (SHA-256), store in MinIO and record the hash
 * in the database, so the delivered file is provably the archived one.
 *
 * <p>Above {@value #PLAFOND_LIGNES} lines the export is rejected (a usability limit, not a technical
 * one). Truncating silently would seal an archive that misrepresents what it covers.
 */
@Service
@RequiredArgsConstructor
public class AuditExportService {

    /** Maximum exported lines, chosen so the file stays readable by a human. */
    public static final int PLAFOND_LIGNES = 20_000;

    private static final Sort TRI = Sort.by(Sort.Direction.DESC, "dateAction").and(Sort.by(Sort.Direction.DESC, "id"));

    private final HistoriqueActionRepository historiqueActionRepository;
    private final ExportAuditRepository exportAuditRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final HistoriqueActionService historiqueActionService;
    private final AuditExportRenderer renderer;
    private final MinioStorageService minioStorageService;
    private final CurrentUserService currentUserService;

    /** The produced file and the record that seals it. */
    public record Extraction(byte[] contenu, ExportAudit archive, String nomFichier) {

        @Override
        public boolean equals(Object autre) {
            return autre instanceof Extraction(byte[] autreContenu, ExportAudit autreArchive, String autreNom)
                    && java.util.Arrays.equals(contenu, autreContenu) && java.util.Objects.equals(archive, autreArchive)
                    && java.util.Objects.equals(nomFichier, autreNom);
        }

        @Override
        public int hashCode() {
            return 31 * java.util.Arrays.hashCode(contenu) + java.util.Objects.hash(archive, nomFichier);
        }

        @Override
        public String toString() {
            return "Extraction[contenu=" + (contenu == null ? "null" : contenu.length + " octets")
                    + ", archive=" + archive + ", nomFichier=" + nomFichier + "]";
        }
    }

    @Transactional
    public Extraction exporter(Perimetre perimetre, Long dossierId, Long utilisateurId,
                                  TypeActionHistorique code, LocalDate dateDebut, LocalDate dateFin,
                                  FormatExportAudit format) {
        if (dateDebut == null || dateFin == null) {
            // Without a period, the hash would prove integrity but never completeness.
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
         * The reference is allocated before generating the file so it can be printed in it; the hash
         * cannot be, since writing it would change the content.
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
                new FiltresAudit(perimetre, dossierId, utilisateurId, code, dateDebut, dateFin), format,
                lignes.size(), empreinte, reference));

        return new Extraction(contenu, archive, nomFichier);
    }

    /** Download URL of an existing archive; the download itself is recorded in the history. */
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
     * Checks whether a hash (computed by the browser) matches a known archive.
     *
     * <p>Deliberately not recorded: it reveals no content, and a hash cannot be enumerated, so the
     * caller must already hold the file.
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
                // An unknown file is a valid answer, not an error: a 404 would look like an outage.
                .orElseGet(() -> new VerificationResponse(false, null));
    }

    /** SHA-256 of the stored bytes (not of the queried data), so the file itself can be verified. */
    static String empreinte(byte[] contenu) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
        } catch (NoSuchAlgorithmException e) {
            // Unreachable in practice: every Java implementation must provide SHA-256.
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private List<LigneExport> mettreEnLignes(List<HistoriqueAction> entrees) {
        /* Author names are resolved in a single query rather than one per line. */
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

    private Map<String, Object> criteres(FiltresAudit filtres, FormatExportAudit format, int lignes,
                                            String empreinte, String reference) {
        Map<String, Object> criteres = new LinkedHashMap<>();
        criteres.put("reference", reference);
        criteres.put("perimetre", filtres.perimetre().name());
        criteres.put("format", format.name());
        criteres.put("dateDebut", filtres.dateDebut().toString());
        criteres.put("dateFin", filtres.dateFin().toString());
        criteres.put("lignes", lignes);
        criteres.put("empreinte", empreinte);
        if (filtres.dossierId() != null) criteres.put("dossierId", filtres.dossierId());
        if (filtres.utilisateurId() != null) criteres.put("auteurRecherche", filtres.utilisateurId());
        if (filtres.code() != null) criteres.put("codeRecherche", filtres.code().name());
        return criteres;
    }

    /** Maps an archive to its API form without exposing the storage key. */
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
