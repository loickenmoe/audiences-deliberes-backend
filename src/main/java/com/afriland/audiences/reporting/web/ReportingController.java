package com.afriland.audiences.reporting.web;

import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.reporting.domain.TypeRapport;
import com.afriland.audiences.reporting.service.JournalAudiencesService;
import com.afriland.audiences.reporting.service.RapportActiviteExportService;
import com.afriland.audiences.reporting.service.RapportActiviteService;
import com.afriland.audiences.reporting.service.RapportExportService;
import com.afriland.audiences.reporting.service.RapportNominatifService;
import com.afriland.audiences.reporting.service.ReportingService;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.reporting.web.dto.ActionDetailleeResponse;
import com.afriland.audiences.reporting.web.dto.AudienceDuJourResponse;
import com.afriland.audiences.reporting.web.dto.RapportActiviteResponse;
import com.afriland.audiences.reporting.web.dto.RapportAvocatsResponse;
import com.afriland.audiences.reporting.web.dto.CompteRenduAttenduResponse;
import com.afriland.audiences.reporting.web.dto.FormatRapport;
import com.afriland.audiences.reporting.web.dto.RapportNominatifResponse;
import com.afriland.audiences.reporting.web.dto.RapportResponse;
import com.afriland.audiences.reporting.web.dto.TableauDeBordResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Épic 8, volet reporting (FR-REP-01/02, US 8.4/8.5) — cf. FUNCTIONAL_MAP.md §8,
 * API_IMPLEMENTATION_STATUS.md §9. US 8.6 (vue consolidée par client) est déjà exposée par
 * {@code GET /clients/{clientId}/dossiers} depuis M4 — cf. `ClientController`.
 */
@RestController
@RequiredArgsConstructor
public class ReportingController {

    private final ReportingService reportingService;
    private final RapportExportService rapportExportService;
    private final RapportNominatifService rapportNominatifService;
    private final RapportActiviteService rapportActiviteService;
    private final RapportActiviteExportService rapportActiviteExportService;
    private final JournalAudiencesService journalAudiencesService;
    /** Pour attribuer l'export dans le pied de page : un document dit qui l'a demandé (A2). */
    private final CurrentUserService currentUserService;

    /**
     * Ouvert au DJ <b>et à la DJA</b> (Q-95). Le critère d'acceptation d'US 8.5 ne nomme que
     * {@code ROLE_DJ}, mais les spécifications fonctionnelles décrivent le tableau de bord consolidé
     * comme obtenu « à travers le DJ/DJA » (partie prenante STK-06) : la DJA instruit les mêmes
     * dossiers et valide conjointement les frais sensibles, lui refuser les indicateurs la priverait
     * du contexte de ses propres décisions. Arbitrage du porteur du projet, 2026-09-14.
     */
    @GetMapping("${api.prefix}/tableau-de-bord")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public TableauDeBordResponse tableauDeBord() {
        return reportingService.tableauDeBord();
    }

    /**
     * Le journal des audiences d'une journée (observation métier du 2026-09-25).
     *
     * <p>Ouvert aux <b>juristes</b> comme à la direction, sur décision du porteur du projet
     * (2026-09-28). D'abord réservé au DJ et à la DJA par prudence — l'écran nomme des personnes et
     * mesure ce qu'elles ont rapporté, et NF-CONF-01 reste « à définir avec la DRH » —, il s'est
     * avéré que les juristes en ont l'usage quotidien : savoir ce qui s'est dit aux audiences du
     * jour fait partie de leur métier, et le compte rendu qu'ils doivent écrire est le leur.
     */
    @GetMapping("${api.prefix}/audiences/journal")
    @PreAuthorize("hasAnyRole('JURISTE', 'DJ', 'DJA')")
    public PageResponse<AudienceDuJourResponse> journalDesAudiences(
            @RequestParam LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return journalAudiencesService.journal(date, PageRequest.of(page, size, Sort.by("id")));
    }

    /**
     * Les audiences passées dont le compte rendu du juriste manque encore.
     *
     * <p>La fenêtre est demandée explicitement, comme pour les rapports et la supervision : sans
     * bornes, la liste grossirait sans fin et cesserait d'être un outil de rappel. Les plus
     * anciennes viennent en tête : ce sont elles qui pressent.
     */
    @GetMapping("${api.prefix}/audiences/comptes-rendus-attendus")
    @PreAuthorize("hasAnyRole('JURISTE', 'DJ', 'DJA')")
    public PageResponse<CompteRenduAttenduResponse> comptesRendusAttendus(
            @RequestParam LocalDate dateDebut,
            @RequestParam LocalDate dateFin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return journalAudiencesService.comptesRendusAttendus(dateDebut, dateFin,
                PageRequest.of(page, size, Sort.by("datePlanifiee", "id")));
    }

    @GetMapping("${api.prefix}/rapports")
    @PreAuthorize("hasRole('JURISTE')")
    public ResponseEntity<?> rapports(@RequestParam TypeRapport type,
                                        @RequestParam LocalDate dateDebut,
                                        @RequestParam LocalDate dateFin,
                                        @RequestParam(defaultValue = "JSON") FormatRapport format) {
        RapportResponse rapport = reportingService.genererRapport(type, dateDebut, dateFin);
        if (rapport.estVide()) {
            return ResponseEntity.noContent().build();
        }
        return switch (format) {
            case JSON -> ResponseEntity.ok(rapport);
            case PDF -> ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=rapport.pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(rapportExportService.exportPdf(rapport, nomDemandeur(), null));
            case EXCEL -> ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=rapport.xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(rapportExportService.exportExcel(rapport));
            case CSV -> ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=rapport.csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(rapportExportService.exportCsv(rapport));
        };
    }

    /**
     * L'activité de chacun sur une période (observation 14, N6).
     *
     * <p><b>Réservé au DJ et à la DJA</b>, et lu sur leurs rôles propres : ce rapport nomme des
     * personnes, et {@code ROLE_JURISTE} étant composite (Q-81), l'ouvrir par lui le donnerait à
     * tous les juristes - chacun lirait l'activité de ses collègues. C'est la même discipline que
     * Q-126.
     *
     * <p>204 quand la période est vide, comme le rapport périodique : une page de zéros laisserait
     * croire que personne n'a rien fait, alors que la période n'a rien à dire.
     */
    @GetMapping("${api.prefix}/rapports/nominatif")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public ResponseEntity<RapportNominatifResponse> rapportNominatif(@RequestParam LocalDate dateDebut,
                                                                     @RequestParam LocalDate dateFin) {
        RapportNominatifResponse rapport = rapportNominatifService.produire(dateDebut, dateFin);
        return rapport.estVide() ? ResponseEntity.noContent().build() : ResponseEntity.ok(rapport);
    }

    /**
     * L'activité des avocats sur une période (Q-136) - le pendant du tableau précédent.
     *
     * <p><b>Pourquoi un second endpoint.</b> Signalé par le porteur du projet le 2026-10-13 :
     * {@code /rapports/nominatif} ne montrait que les profils internes. C'était exact, et ce n'était
     * pas réparable en y ajoutant les avocats - ce tableau compte six natures de production interne
     * dont un avocat n'accomplit aucune. Il <b>dépose</b> ; c'est le juriste qui enregistre. L'y
     * faire figurer n'aurait affiché qu'une ligne de zéros, soit ce que la conception refuse.
     *
     * <p>Mêmes habilitations que le tableau interne, et pour la même raison : il nomme des
     * personnes, et {@code ROLE_JURISTE} est composite (Q-81).
     */
    @GetMapping("${api.prefix}/rapports/nominatif/avocats")
    @PreAuthorize("hasAnyRole('DJ', 'DJA')")
    public ResponseEntity<RapportAvocatsResponse> rapportAvocats(@RequestParam LocalDate dateDebut,
                                                                  @RequestParam LocalDate dateFin) {
        RapportAvocatsResponse rapport = rapportNominatifService.produireAvocats(dateDebut, dateFin);
        return rapport.estVide() ? ResponseEntity.noContent().build() : ResponseEntity.ok(rapport);
    }

    /**
     * La fiche d'activité d'une personne (Q-133) : <b>chacun sur lui-même</b>, le DJ et la DJA sur
     * quiconque.
     *
     * <p><b>Ouvert aussi à l'avocat depuis le 2026-10-13 (Q-135), et j'avais écrit l'inverse.</b>
     * J'ai soutenu qu'« aucune des natures comptées n'est la sienne - sa fiche serait vide par
     * construction ». C'était <b>faux</b> : il dépose ses demandes de frais, ses comptes rendus
     * d'audience, ses autres publications, et répond aux demandes d'explication - quatre natures,
     * toutes journalisées sous son propre compte. Le métier a demandé qu'il voie et télécharge son
     * activité, et que la direction puisse la lire.
     *
     * <p>{@code ROLE_CONSULTATION} couvre les cinq profils internes ; {@code ROLE_AVOCAT} s'y
     * ajoute, l'avocat ne portant pas le premier (QF-01). Deux rôles nommés plutôt que sept
     * profils : ce qui est énuméré ici doit rester court, ou il vieillira.
     *
     * <p><b>C'est le service qui décide sur qui.</b> L'annotation dit seulement qui peut toucher à
     * la fonction : {@code utilisateurId} n'est servi qu'à la direction, ou à la personne qui se
     * demande elle-même. Un {@code @PreAuthorize} ne sait pas comparer un paramètre à l'appelant.
     *
     * <p>204 quand la personne n'a rien fait sur la période : un état vide, pas une erreur.
     */
    @GetMapping("${api.prefix}/rapports/activite")
    @PreAuthorize("hasAnyRole('CONSULTATION', 'AVOCAT')")
    public ResponseEntity<RapportActiviteResponse> activite(@RequestParam LocalDate dateDebut,
                                                            @RequestParam LocalDate dateFin,
                                                            @RequestParam(required = false) Long utilisateurId,
                                                            @RequestParam(required = false) Long avocatId) {
        RapportActiviteResponse rapport =
                rapportActiviteService.produire(utilisateurId, avocatId, dateDebut, dateFin);
        return rapport.estVide() ? ResponseEntity.noContent().build() : ResponseEntity.ok(rapport);
    }

    /**
     * La même fiche, en document : « chaque utilisateur doit être capable de télécharger ses propres
     * rapports » (métier, 2026-10-09).
     *
     * <p>Le nom du fichier porte la personne et la période - une direction qui télécharge cinq
     * fiches le même jour doit les distinguer sans les ouvrir. Il est construit ici parce que
     * l'en-tête {@code Content-Disposition} n'est pas exposé en CORS : le frontend ne peut pas le
     * lire, et devrait le reconstruire de son côté, où il divergerait.
     *
     * <p>Pas de {@code JSON} dans les formats admis : c'est l'endpoint précédent. Un format de plus
     * ici obligerait à choisir entre deux chemins pour la même chose.
     */
    @GetMapping("${api.prefix}/rapports/activite/export")
    @PreAuthorize("hasAnyRole('CONSULTATION', 'AVOCAT')")
    public ResponseEntity<byte[]> activiteExport(@RequestParam LocalDate dateDebut,
                                                  @RequestParam LocalDate dateFin,
                                                  @RequestParam(required = false) Long utilisateurId,
                                                  @RequestParam(required = false) Long avocatId,
                                                  @RequestParam FormatRapport format) {
        RapportActiviteResponse rapport =
                rapportActiviteService.produire(utilisateurId, avocatId, dateDebut, dateFin);
        if (rapport.estVide()) {
            return ResponseEntity.noContent().build();
        }
        return switch (format) {
            case JSON -> throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE,
                    "Le format JSON se lit sur /rapports/activite, non sur son export.");
            case PDF -> fichier(rapportActiviteExportService.exportPdf(rapport),
                    rapportActiviteExportService.nomFichier(rapport, "pdf"), MediaType.APPLICATION_PDF);
            case EXCEL -> fichier(rapportActiviteExportService.exportExcel(rapport),
                    rapportActiviteExportService.nomFichier(rapport, "xlsx"),
                    MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            case CSV -> fichier(rapportActiviteExportService.exportCsv(rapport),
                    rapportActiviteExportService.nomFichier(rapport, "csv"),
                    MediaType.parseMediaType("text/csv"));
        };
    }

    /**
     * Les actions derrière un décompte de la fiche (Q-133) : « cliquer sur un élément doit renvoyer
     * aux détails de la dite tâche » (métier, 2026-10-09).
     *
     * <p>Chaque ligne porte son {@code dossierId} et la référence lisible du dossier : c'est ce qui
     * permet à l'écran d'ouvrir la fiche du dossier concerné. Un décompte à quatre ne mène nulle
     * part ; les quatre actions, si.
     *
     * <p>Même périmètre que la fiche, tenu par le même service : sans cela, le détail serait une
     * porte dérobée vers l'activité d'un collègue que le décompte, lui, protège.
     */
    @GetMapping("${api.prefix}/rapports/activite/details")
    @PreAuthorize("hasAnyRole('CONSULTATION', 'AVOCAT')")
    public PageResponse<ActionDetailleeResponse> activiteDetails(@RequestParam LocalDate dateDebut,
                                                                  @RequestParam LocalDate dateFin,
                                                                  @RequestParam TypeActionHistorique code,
                                                                  @RequestParam(required = false) Long utilisateurId,
                                                                  @RequestParam(required = false) Long avocatId,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(rapportActiviteService.detailler(
                utilisateurId, avocatId, code, dateDebut, dateFin, PageRequest.of(page, size)));
    }

    /** Un document à télécharger : même en-tête pour les trois formats, pour qu'ils ne divergent pas. */
    private static ResponseEntity<byte[]> fichier(byte[] contenu, String nom, MediaType type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nom + "\"")
                .contentType(type)
                .body(contenu);
    }

    /**
     * Le nom de qui demande l'export, pour le pied de page. Jamais bloquant : un document se produit
     * même si le nom manque, le pied portant alors la date d'édition.
     */
    private String nomDemandeur() {
        try {
            var utilisateur = currentUserService.getCurrentUser();
            return utilisateur == null ? null : utilisateur.getPrenom() + " " + utilisateur.getNom();
        } catch (RuntimeException ignore) {
            return null;
        }
    }
}
