package com.afriland.audiences.intervenant.service;

import com.afriland.audiences.common.audit.TypeActionHistorique;
import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.web.PageResponse;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.ged.domain.Document;
import com.afriland.audiences.ged.domain.FormatDocument;
import com.afriland.audiences.ged.repository.DocumentRepository;
import com.afriland.audiences.ged.service.MinioStorageService;
import com.afriland.audiences.intervenant.domain.DemandeConstitution;
import com.afriland.audiences.intervenant.domain.LettreConstitution;
import com.afriland.audiences.intervenant.domain.StatutConstitution;
import com.afriland.audiences.intervenant.mapper.DemandeConstitutionMapper;
import com.afriland.audiences.intervenant.repository.DemandeConstitutionRepository;
import com.afriland.audiences.intervenant.repository.LettreConstitutionRepository;
import com.afriland.audiences.intervenant.web.dto.CreerConstitutionRequest;
import com.afriland.audiences.intervenant.web.dto.DemandeConstitutionResponse;
import com.afriland.audiences.intervenant.web.dto.LettreConstitutionResponse;
import com.afriland.audiences.intervenant.web.dto.RedigerLettreRequest;
import com.afriland.audiences.intervenant.web.dto.ValiderConstitutionRequest;
import com.afriland.audiences.notification.domain.EvenementPrestataire;
import com.afriland.audiences.notification.service.NotificationPrestataireService;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Intervenant;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.IntervenantRepository;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import com.afriland.audiences.referentiel.service.SignatureService;
import com.afriland.audiences.referentiel.service.CompteAvocatService;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Provider appointments (RG-INT-08, US 5.6/5.7). */
@Service
@RequiredArgsConstructor
public class ConstitutionService {

    private static final String CLE_DEMANDE_ID = "demandeId";

    /**
     * Document type of a signed letter in the GED. {@code typeDocument} is free text, so this
     * constant is the single source of its value; the UI relies on it to offer pre-printed paper
     * printing, and an end-to-end test checks both sides agree.
     */
    public static final String TYPE_DOCUMENT_LETTRE = "Lettre de constitution";

    private final DemandeConstitutionRepository demandeConstitutionRepository;
    private final DossierRepository dossierRepository;
    private final IntervenantRepository intervenantRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CurrentUserService currentUserService;
    private final HistoriqueActionService historiqueActionService;
    private final AlerteService alerteService;
    private final LettreConstitutionService lettreConstitutionService;
    /**
     * Line manager profile code. Checked on the profile rather than a role: {@code ROLE_JURISTE} is
     * composite, so a role-based grant would extend to DJ and DJA.
     */
    private static final String PROFIL_SH = "SH";

    private final SignatureService signatureService;
    private final LettreConstitutionRepository lettreConstitutionRepository;
    private final MinioStorageService minioStorageService;
    private final DocumentRepository documentRepository;
    private final DemandeConstitutionMapper demandeConstitutionMapper;
    private final CompteAvocatService compteAvocatService;
    private final NotificationPrestataireService notificationPrestataireService;

    @Transactional
    public DemandeConstitutionResponse creerDemande(CreerConstitutionRequest request) {
        Dossier dossier = dossierRepository.findById(request.dossierId())
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Dossier introuvable : " + request.dossierId()));
        Intervenant prestataire = intervenantRepository.findById(request.prestataireId())
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Prestataire introuvable : " + request.prestataireId()));
        Utilisateur demandeur = currentUserService.getCurrentUser();

        DemandeConstitution demande = demandeConstitutionRepository.save(DemandeConstitution.builder()
                .dossier(dossier)
                .prestataire(prestataire)
                .demandeur(demandeur)
                .motif(request.motif())
                .statut(StatutConstitution.EN_ATTENTE)
                .build());

        // The draft letter is created with the request, without a number, so it can be drafted
        // immediately without wasting a number on an abandoned request.
        lettreConstitutionRepository.save(lettreConstitutionService.brouillonPour(demande, null));

        historiqueActionService.enregistrerPour(demandeur, dossier.getId(), TypeActionHistorique.CONSTITUTION_DEMANDEE,
                Map.of(CLE_DEMANDE_ID, demande.getId(), "prestataireId", prestataire.getId()));

        String declencheur = "Demande de constitution du prestataire « " + prestataire.getNom()
                + " » sur le dossier " + dossier.getReference() + " (RG-INT-08).";
        List<Utilisateur> sh = utilisateurRepository.findByProfilCodeAndActifTrue("SH");
        sh.forEach(u -> alerteService.declencher(TypeAlerte.CONSTITUTION_A_SIGNER, dossier.getId(), u, declencheur));

        return versReponse(demande);
    }

    @Transactional
    public DemandeConstitutionResponse valider(Long demandeId, ValiderConstitutionRequest request) {
        DemandeConstitution demande = demandeConstitutionRepository.findById(demandeId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE, "Demande de constitution introuvable : " + demandeId));
        if (demande.getStatut() != StatutConstitution.EN_ATTENTE) {
            throw ApiException.conflict(ErrorCodes.CONFLIT, "Cette demande a déjà été traitée");
        }

        Utilisateur sh = currentUserService.getCurrentUser();
        demande.setValidePar(sh);
        demande.setDateDecision(LocalDateTime.now());

        if (request.decision() == StatutConstitution.VALIDEE) {
            demande.setStatut(StatutConstitution.VALIDEE);
            genererEtAttacherLettre(demande, sh);
            notifierPrestataire(demande);
        } else {
            if (!StringUtils.hasText(request.motifRejet())) {
                throw ApiException.badRequest(ErrorCodes.REQUETE_INVALIDE, "Le motif de rejet est obligatoire");
            }
            demande.setStatut(StatutConstitution.REJETEE);
            demande.setMotifRejet(request.motifRejet());
            alerteService.declencher(TypeAlerte.CONSTITUTION_A_SIGNER, demande.getDossier().getId(), demande.getDemandeur(),
                    "Votre demande de constitution du prestataire « " + demande.getPrestataire().getNom()
                            + " » a été rejetée : " + request.motifRejet());
        }
        demande = demandeConstitutionRepository.save(demande);

        historiqueActionService.enregistrerPour(sh, demande.getDossier().getId(), TypeActionHistorique.CONSTITUTION_ARBITREE,
                Map.of(CLE_DEMANDE_ID, demandeId, "decision", request.decision().name()));

        return versReponse(demande);
    }

    /**
     * Line manager's "appointments to sign" queue: {@code statut=EN_ATTENTE} feeds that screen;
     * without a filter, the full history.
     */
    @Transactional(readOnly = true)
    public PageResponse<DemandeConstitutionResponse> lister(StatutConstitution statut, Pageable pageable) {
        var demandes = statut == null
                ? demandeConstitutionRepository.findByOrderByDateDemandeDesc(pageable)
                : demandeConstitutionRepository.findByStatutOrderByDateDemandeAsc(statut, pageable);
        // Each row states whether the current reader may write the letter, so the UI never offers
        // an action that would end in a 403.
        return PageResponse.of(demandes, this::versReponse);
    }

    /**
     * The letter of a request, draft or frozen. {@code modifiable} tells the UI whether the current
     * reader may still edit it.
     */
    @Transactional(readOnly = true)
    public LettreConstitutionResponse lettre(Long demandeId) {
        return versReponse(lettreOuErreur(demandeId));
    }

    /**
     * Prints the letter for the bank's pre-printed paper, from the document archived in the GED.
     * Regenerated from the stored fields, never stored, so there is a single authoritative copy.
     * Any document that is not a signed letter is rejected.
     */
    @Transactional(readOnly = true)
    public byte[] lettrePourPapierPreimprime(Long documentId) {
        DemandeConstitution demande = demandeConstitutionRepository.findByLettreDocumentId(documentId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Ce document n'est pas une lettre de constitution : " + documentId));
        LettreConstitution lettre = lettreConstitutionRepository.findByDemandeId(demande.getId())
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Lettre introuvable pour la demande " + demande.getId()));
        // Same signatures as the archived copy: both are prints of the same letter.
        return lettreConstitutionService.generer(lettre, true,
                signataire(redacteurDe(lettre)), signataire(demande.getValidePar()));
    }

    /**
     * Saves a draft by the requester or the line manager, who may correct before signing; each
     * version records its author, shown on screen.
     *
     * <p>A generated letter is never rewritten: a correction requires a new request, hence a new
     * letter and number, and the first remains available.
     */
    @Transactional
    public LettreConstitutionResponse redigerLettre(Long demandeId, RedigerLettreRequest requete) {
        LettreConstitution lettre = lettreOuErreur(demandeId);
        if (!puisJeRediger(lettre.getDemande())) {
            throw ApiException.forbidden(ErrorCodes.DROITS_INSUFFISANTS,
                    "Cette lettre appartient au demandeur de la constitution : lui seul l'écrit, "
                            + "et le supérieur hiérarchique la corrige avant de la signer.");
        }
        if (lettre.estGelee()) {
            throw ApiException.conflict(ErrorCodes.CONFLIT,
                    "Cette lettre a été signée et versée au dossier : elle ne peut plus être modifiée. "
                            + "Une correction passe par une nouvelle demande de constitution.");
        }
        if (requete.numero() != null && !requete.numero().equals(lettre.getNumero())
                && lettreConstitutionRepository.existsByNumero(requete.numero())) {
            throw ApiException.conflict(ErrorCodes.CONFLIT,
                    "Le numéro " + requete.numero() + " est déjà porté par une autre lettre");
        }

        lettre.setNumero(requete.numero());
        lettre.setSuffixeNumero(requete.suffixeNumero());
        lettre.setLieu(requete.lieu());
        lettre.setDestinataireNom(requete.destinataireNom());
        lettre.setDestinataireQualite(requete.destinataireQualite());
        lettre.setDestinataireVille(requete.destinataireVille());
        lettre.setConcerne(requete.concerne());
        lettre.setObjet(requete.objet());
        lettre.setCorps(requete.corps());
        lettre.setSignataire(requete.signataire());
        Utilisateur redacteur = currentUserService.getCurrentUser();
        lettre.setRedigePar(redacteur);
        lettre = lettreConstitutionRepository.save(lettre);

        // The letter commits the bank: log who drafted it, with their profile, since the line
        // manager may also edit it.
        historiqueActionService.enregistrerPour(redacteur,
                lettre.getDemande().getDossier().getId(), TypeActionHistorique.LETTRE_CONSTITUTION_REDIGEE,
                Map.of(CLE_DEMANDE_ID, demandeId,
                        // `Map.of` rejects nulls; the subject and the profile may both be missing.
                        "objet", Optional.ofNullable(requete.objet()).orElse(""),
                        "profilRedacteur", Optional.ofNullable(profil(redacteur)).orElse("")));

        return versReponse(lettre);
    }

    /**
     * Whether the current user may write this request's letter: only its requester, plus the line
     * manager (checked on the profile, since {@code ROLE_JURISTE} is composite and would let any
     * legal officer, DJ or DJA write under someone else's name).
     */
    private boolean puisJeRediger(DemandeConstitution demande) {
        Utilisateur moi = currentUserService.getCurrentUser();
        if (moi == null) {
            return false;
        }
        return PROFIL_SH.equals(profil(moi)) || estLaMemePersonne(moi, demande.getDemandeur());
    }

    private static boolean estLaMemePersonne(Utilisateur un, Utilisateur autre) {
        return un != null && autre != null && un.getId() != null && un.getId().equals(autre.getId());
    }

    /** The request as read by the UI, with the current reader's write permission. */
    private DemandeConstitutionResponse versReponse(DemandeConstitution demande) {
        return demandeConstitutionMapper.toResponse(demande).avecRedactionPossible(puisJeRediger(demande));
    }

    private LettreConstitution lettreOuErreur(Long demandeId) {
        return lettreConstitutionRepository.findByDemandeId(demandeId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Aucune lettre pour la demande " + demandeId));
    }

    private LettreConstitutionResponse versReponse(LettreConstitution lettre) {
        return new LettreConstitutionResponse(
                lettre.getId(), lettre.getDemande().getId(), lettre.getNumero(),
                lettre.getNumero(), lettre.getSuffixeNumero(), lettre.getLieu(),
                lettre.getDestinataireNom(), lettre.getDestinataireQualite(), lettre.getDestinataireVille(),
                lettre.getConcerne(), lettre.getObjet(), lettre.getCorps(), lettre.getSignataire(),
                // `modifiable` means "by the current reader": the UI enables its fields on this alone.
                !lettre.estGelee() && puisJeRediger(lettre.getDemande()),
                lettre.getDateGeneration(), lettre.getDateModification(),
                nomDu(lettre.getRedigePar()), profil(lettre.getRedigePar()));
    }

    /**
     * Drafter shown in the left signature column: the recorded drafter, except when the line
     * manager corrected the letter (they would appear on both sides), in which case the requester
     * is used. The correction remains traced on screen and in the history.
     */
    private static Utilisateur redacteurDe(LettreConstitution lettre) {
        Utilisateur redacteur = lettre.getRedigePar();
        Utilisateur valideur = lettre.getDemande() == null ? null : lettre.getDemande().getValidePar();
        boolean memePersonne = redacteur != null && valideur != null
                && redacteur.getId() != null && redacteur.getId().equals(valideur.getId());
        if (redacteur == null || memePersonne) {
            return lettre.getDemande() == null ? null : lettre.getDemande().getDemandeur();
        }
        return redacteur;
    }

    /** A person and their stored signature; the image may be missing, the name may not. */
    private LettreConstitutionService.Signataire signataire(Utilisateur utilisateur) {
        if (utilisateur == null) {
            return null;
        }
        byte[] image = signatureService.pour(utilisateur.getId()).map(s -> s.image()).orElse(null);
        return new LettreConstitutionService.Signataire(image, nomDu(utilisateur));
    }

    /** Full name, or null (a draft exists before anyone has written it). */
    private static String nomDu(Utilisateur utilisateur) {
        if (utilisateur == null) {
            return null;
        }
        String prenom = utilisateur.getPrenom() == null ? "" : utilisateur.getPrenom() + " ";
        return (prenom + (utilisateur.getNom() == null ? "" : utilisateur.getNom())).trim();
    }

    /** Profile code (e.g. `JURISTE`, `SH`), translated by the UI. */
    private static String profil(Utilisateur utilisateur) {
        if (utilisateur == null || utilisateur.getProfil() == null) {
            return null;
        }
        return utilisateur.getProfil().getCode();
    }

    /**
     * RG-INT-08: validation produces the letter, stored in the GED. The number is assigned here,
     * when the file is produced, not when the draft is opened, so abandoned drafts leave no gaps.
     */
    private void genererEtAttacherLettre(DemandeConstitution demande, Utilisateur sh) {
        LettreConstitution lettre = lettreConstitutionRepository.findByDemandeId(demande.getId())
                .orElseGet(() -> lettreConstitutionService.brouillonPour(demande, null));
        // Drawn from the sequence unless the drafter imposed one.
        if (lettre.getNumero() == null) {
            lettre.setNumero((int) lettreConstitutionRepository.prochainNumero());
        }
        lettre.setDateGeneration(LocalDateTime.now());
        lettre = lettreConstitutionRepository.save(lettre);

        /*
         * Signatures come from the accounts of the two people who made the letter, never from the
         * free-text "signataire" field: that would allow signing on someone else's behalf.
         */
        byte[] pdf = lettreConstitutionService.generer(lettre,
                signataire(redacteurDe(lettre)), signataire(sh));
        String nomFichier = "lettre-constitution-" + lettre.getNumero() + ".pdf";
        String cle = minioStorageService.deposer(demande.getDossier().getId(), nomFichier, "application/pdf", pdf);

        Document document = documentRepository.save(Document.builder()
                .dossier(demande.getDossier())
                .nomFichier(nomFichier)
                .typeDocument(TYPE_DOCUMENT_LETTRE)
                .format(FormatDocument.PDF)
                .poids((long) pdf.length)
                .auteurChargement(sh)
                .estSupprime(false)
                .cheminStockage(cle)
                .build());

        demande.setLettreDocument(document);
    }

    /** The letter generated at validation, if it was archived in the GED. */
    private NotificationPrestataireService.PieceJointe lettreAJoindre(DemandeConstitution demande) {
        Document lettre = demande.getLettreDocument();
        if (lettre == null || lettre.getCheminStockage() == null) {
            return null;
        }
        return new NotificationPrestataireService.PieceJointe(lettre.getId(), lettre.getNomFichier(),
                "application/pdf", lettre.getCheminStockage());
    }

    /**
     * Notifies the appointed provider. The in-app alert is limited to an {@link Avocat} with an
     * account (RG-INT-01); external channels use the contact details and so also reach other
     * providers.
     */
    private void notifierPrestataire(DemandeConstitution demande) {
        Long dossierId = demande.getDossier().getId();
        // The letter is attached to the notification; only email delivers it.
        notificationPrestataireService.notifier(EvenementPrestataire.CONSTITUTION_VALIDEE, demande.getPrestataire(),
                dossierId, "constitution-" + demande.getId(), lettreAJoindre(demande));
        // The lazily loaded provider is an Intervenant proxy, never "instanceof Avocat": unproxy first.
        if (!(Hibernate.unproxy(demande.getPrestataire()) instanceof Avocat avocat)) {
            return;
        }
        compteAvocatService.utilisateurDe(avocat).ifPresent(destinataire ->
                alerteService.declencher(TypeAlerte.CONSTITUTION_A_SIGNER, dossierId, destinataire,
                        "Votre constitution sur le dossier " + demande.getDossier().getReference()
                                + " a été validée ; la lettre de constitution est disponible dans la GED du dossier."));
    }
}
