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

/** Épic 5 volet constitutions (RG-INT-08, US 5.6/5.7) — cf. FUNCTIONAL_MAP.md §5, API_IMPLEMENTATION_STATUS.md §7. */
@Service
@RequiredArgsConstructor
public class ConstitutionService {

    private final DemandeConstitutionRepository demandeConstitutionRepository;
    private final DossierRepository dossierRepository;
    private final IntervenantRepository intervenantRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CurrentUserService currentUserService;
    private final HistoriqueActionService historiqueActionService;
    private final AlerteService alerteService;
    private final LettreConstitutionService lettreConstitutionService;
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

        /*
         * La lettre naît avec la demande (B2), **sans numéro** : le juriste doit pouvoir la rédiger
         * aussitôt, et réserver un numéro dès maintenant en brûlerait un à chaque demande sans
         * suite — dans une série de courrier officiel qui ne repart jamais à un.
         */
        lettreConstitutionRepository.save(lettreConstitutionService.brouillonPour(demande, null));

        historiqueActionService.enregistrerPour(demandeur, dossier.getId(), TypeActionHistorique.CONSTITUTION_DEMANDEE,
                Map.of("demandeId", demande.getId(), "prestataireId", prestataire.getId()));

        String declencheur = "Demande de constitution du prestataire « " + prestataire.getNom()
                + " » sur le dossier " + dossier.getReference() + " (RG-INT-08).";
        List<Utilisateur> sh = utilisateurRepository.findByProfilCodeAndActifTrue("SH");
        sh.forEach(u -> alerteService.declencher(TypeAlerte.CONSTITUTION_A_SIGNER, dossier.getId(), u, declencheur));

        return demandeConstitutionMapper.toResponse(demande);
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
                Map.of("demandeId", demandeId, "decision", request.decision().name()));

        return demandeConstitutionMapper.toResponse(demande);
    }

    /**
     * M15 (Q-68) : file de travail « constitutions à signer » du SH — endpoint 🆕.
     * {@code statut=EN_ATTENTE} alimente son écran ; sans filtre, l'historique complet.
     */
    @Transactional(readOnly = true)
    public PageResponse<DemandeConstitutionResponse> lister(StatutConstitution statut, Pageable pageable) {
        var demandes = statut == null
                ? demandeConstitutionRepository.findByOrderByDateDemandeDesc(pageable)
                : demandeConstitutionRepository.findByStatutOrderByDateDemandeAsc(statut, pageable);
        return PageResponse.of(demandes, demandeConstitutionMapper::toResponse);
    }

    /**
     * La lettre d'une demande — brouillon ou pièce gelée (B2).
     *
     * <p>Le juriste la rédige, le supérieur hiérarchique la consulte avant de signer : c'est le même
     * document, lu par deux rôles aux droits différents. {@code modifiable} dit lequel des deux
     * usages reste ouvert, plutôt que de laisser l'écran le deviner.
     */
    @Transactional(readOnly = true)
    public LettreConstitutionResponse lettre(Long demandeId) {
        return versReponse(lettreOuErreur(demandeId));
    }

    /**
     * Enregistre la rédaction du juriste (B2).
     *
     * <p><b>Une lettre générée ne se réécrit pas.</b> Elle est partie chez un huissier et figure dans
     * la GED : la retoucher en silence ferait mentir la pièce. Une correction passe par une nouvelle
     * demande, donc une nouvelle lettre et un nouveau numéro — et la première reste consultable.
     */
    @Transactional
    public LettreConstitutionResponse redigerLettre(Long demandeId, RedigerLettreRequest requete) {
        LettreConstitution lettre = lettreOuErreur(demandeId);
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
        lettre = lettreConstitutionRepository.save(lettre);

        // La lettre engage la banque : savoir qui l'a rédigée, et quand, fait partie de la pièce.
        historiqueActionService.enregistrerPour(currentUserService.getCurrentUser(),
                lettre.getDemande().getDossier().getId(), TypeActionHistorique.LETTRE_CONSTITUTION_REDIGEE,
                Map.of("demandeId", demandeId, "objet", requete.objet()));

        return versReponse(lettre);
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
                !lettre.estGelee(), lettre.getDateGeneration(), lettre.getDateModification());
    }

    /**
     * RG-INT-08 : la validation produit la lettre, stockée dans la GED (M8).
     *
     * <p><b>La lettre est désormais un document, plus un calcul (Q-115 B1).</b> Son contenu est
     * enregistré avant d'être mis en page : c'est ce qui permettra, au jalon suivant, de le laisser
     * rédiger par le juriste — et c'est déjà ce qui répond à « pourquoi cette lettre dit-elle ceci ? ».
     *
     * <p>Le numéro est <b>réservé ici</b>, au moment de produire le fichier, et non à l'ouverture
     * d'un brouillon : un brouillon abandonné brûlerait un numéro dans une série qui ne repart
     * jamais à un.
     */
    private void genererEtAttacherLettre(DemandeConstitution demande, Utilisateur sh) {
        LettreConstitution lettre = lettreConstitutionRepository.findByDemandeId(demande.getId())
                .orElseGet(() -> lettreConstitutionService.brouillonPour(demande, null));
        // Le numéro n'est tiré qu'ici, sauf si le rédacteur en a imposé un.
        if (lettre.getNumero() == null) {
            lettre.setNumero((int) lettreConstitutionRepository.prochainNumero());
        }
        lettre.setDateGeneration(LocalDateTime.now());
        lettre = lettreConstitutionRepository.save(lettre);

        byte[] pdf = lettreConstitutionService.generer(lettre);
        String nomFichier = "lettre-constitution-" + lettre.getNumero() + ".pdf";
        String cle = minioStorageService.deposer(demande.getDossier().getId(), nomFichier, "application/pdf", pdf);

        Document document = documentRepository.save(Document.builder()
                .dossier(demande.getDossier())
                .nomFichier(nomFichier)
                .typeDocument("Lettre de constitution")
                .format(FormatDocument.PDF)
                .poids((long) pdf.length)
                .auteurChargement(sh)
                .estSupprime(false)
                .cheminStockage(cle)
                .build());

        demande.setLettreDocument(document);
    }

    /**
     * Notifie le prestataire constitué. L'alerte applicative reste réservée à un {@link Avocat}
     * disposant d'un compte (RG-INT-01) ; les canaux externes (Q-118), eux, visent les coordonnées de
     * la fiche et touchent donc aussi l'autre prestataire, qui n'était jusqu'ici jamais prévenu (Q-52).
     */
    private void notifierPrestataire(DemandeConstitution demande) {
        Long dossierId = demande.getDossier().getId();
        notificationPrestataireService.notifier(EvenementPrestataire.CONSTITUTION_VALIDEE, demande.getPrestataire(),
                dossierId, "constitution-" + demande.getId());
        // Q-87 : le prestataire chargé à la demande est un proxy d'Intervenant, jamais « instanceof Avocat ».
        if (!(Hibernate.unproxy(demande.getPrestataire()) instanceof Avocat avocat)) {
            return;
        }
        compteAvocatService.utilisateurDe(avocat).ifPresent(destinataire ->
                alerteService.declencher(TypeAlerte.CONSTITUTION_A_SIGNER, dossierId, destinataire,
                        "Votre constitution sur le dossier " + demande.getDossier().getReference()
                                + " a été validée ; la lettre de constitution est disponible dans la GED du dossier."));
    }
}
