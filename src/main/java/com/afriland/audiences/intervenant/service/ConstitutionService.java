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

/** Épic 5 volet constitutions (RG-INT-08, US 5.6/5.7) — cf. FUNCTIONAL_MAP.md §5, API_IMPLEMENTATION_STATUS.md §7. */
@Service
@RequiredArgsConstructor
public class ConstitutionService {

    /**
     * Le type de pièce porté par une lettre signée dans la GED. {@code typeDocument} est un texte
     * libre (Q-45) : cette constante est donc le seul point où sa valeur est décidée. L'interface
     * s'y réfère pour proposer le tirage sur papier préimprimé, et un parcours de bout en bout
     * vérifie que les deux côtés parlent bien de la même chaîne.
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
     * Le profil du supérieur hiérarchique, lu tel quel.
     *
     * <p>Et non un rôle : {@code ROLE_JURISTE} est composite (Q-81), si bien qu'un droit accordé par
     * rôle s'étend au DJ et à la DJA sans qu'on l'ait voulu. Le profil ne désigne qu'une personne à
     * la fois.
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
                Map.of("demandeId", demandeId, "decision", request.decision().name()));

        return versReponse(demande);
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
        /*
         * Chaque ligne dit si **celui qui lit** peut écrire sur sa lettre (Q-126). Sans cela,
         * l'écran proposerait « Rédiger » à qui recevrait ensuite un 403 - une porte qui s'ouvre
         * sur un mur est pire qu'une porte absente.
         */
        return PageResponse.of(demandes, this::versReponse);
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
     * Le tirage de la lettre <b>pour le papier préimprimé</b> de la banque, à partir de la pièce
     * archivée dans la GED (observation métier du 2026-09-25).
     *
     * <p><b>Régénéré, jamais stocké.</b> La lettre est enregistrée champ par champ : le PDF se
     * refabrique donc à l'identique, sans décor. Archiver un second fichier aurait posé la question
     * de l'exemplaire qui fait foi — et l'aurait posée le jour où l'un des deux aurait divergé.
     *
     * <p>Refuse tout document qui n'est pas une lettre signée : la GED contient des pièces de toutes
     * sortes, dont aucune autre n'est régénérable.
     */
    @Transactional(readOnly = true)
    public byte[] lettrePourPapierPreimprime(Long documentId) {
        DemandeConstitution demande = demandeConstitutionRepository.findByLettreDocumentId(documentId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Ce document n'est pas une lettre de constitution : " + documentId));
        LettreConstitution lettre = lettreConstitutionRepository.findByDemandeId(demande.getId())
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.RESSOURCE_INTROUVABLE,
                        "Lettre introuvable pour la demande " + demande.getId()));
        /*
         * Le tirage pour papier préimprimé porte les mêmes signatures que l'exemplaire archivé : ce
         * sont deux tirages d'une même lettre, et l'un sans signature ferait douter de l'autre.
         */
        return lettreConstitutionService.generer(lettre, true,
                signataire(redacteurDe(lettre)), signataire(demande.getValidePar()));
    }

    /**
     * Enregistre la rédaction, du juriste <b>ou du SH</b> (B2, amendée le 2026-10-06).
     *
     * <p><b>Ce qui a changé, et pourquoi.</b> Q-115 B2 réservait la plume au juriste : le SH
     * consultait sans pouvoir récrire, car « lui donner la plume viderait la seconde signature de
     * son sens ». Le métier a tranché l'inverse (observation 10) : le SH corrige la lettre avant de
     * la signer, plutôt que de la renvoyer. La séparation des plumes cède, et la <b>traçabilité</b>
     * la remplace : chaque rédaction porte son auteur, rendu à l'écran, pour que le juriste voie que
     * sa lettre a été réécrite et par qui.
     *
     * <p><b>Une lettre générée ne se réécrit pas.</b> Cela, en revanche, ne bouge pas. Elle est
     * partie chez un huissier et figure dans la GED : la retoucher en silence ferait mentir la
     * pièce. Une correction passe par une nouvelle demande, donc une nouvelle lettre et un nouveau
     * numéro — et la première reste consultable.
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

        /*
         * La lettre engage la banque : savoir qui l'a rédigée, et quand, fait partie de la pièce.
         * Le profil est consigné avec le nom : depuis que le SH peut corriger (2026-10-06), lire
         * « rédigée par X » sans savoir à quel titre ne dit plus si la séparation a été franchie.
         */
        historiqueActionService.enregistrerPour(redacteur,
                lettre.getDemande().getDossier().getId(), TypeActionHistorique.LETTRE_CONSTITUTION_REDIGEE,
                Map.of("demandeId", demandeId,
                        // `Map.of` refuse les valeurs nulles : un objet non renseigné est permis
                        // (Q-115 B2, le gabarit se complète à la main), et un profil peut manquer.
                        "objet", Optional.ofNullable(requete.objet()).orElse(""),
                        "profilRedacteur", Optional.ofNullable(profil(redacteur)).orElse("")));

        return versReponse(lettre);
    }

    /**
     * Puis-je écrire sur la lettre de cette demande (métier, 2026-10-07) ?
     *
     * <p><b>La lettre est à celui qui a sollicité la constitution.</b> Jusqu'ici le droit tenait au
     * rôle, et {@code ROLE_JURISTE} étant composite (Q-81), tout juriste, le DJ et la DJA pouvaient
     * récrire la lettre d'un autre. C'était un trou : une lettre engage la banque sous le nom de
     * celui qui l'a écrite, et nul ne doit pouvoir écrire sous le nom d'un autre.
     *
     * <p><b>Le supérieur hiérarchique passe après lui, et lui seul.</b> C'est la contrepartie de
     * Q-124 : il corrige au lieu de renvoyer la lettre. Le droit se lit sur le <b>profil</b>, non
     * sur un rôle composite - c'est la seule façon de ne pas rouvrir la porte au DJ et à la DJA.
     */
    private boolean puisJeRediger(DemandeConstitution demande) {
        Utilisateur moi = currentUserService.getCurrentUser();
        if (moi == null) {
            return false;
        }
        return PROFIL_SH.equals(profil(moi)) || estLaMemePersonne(moi, demande.getDemandeur());
    }

    /** Deux utilisateurs sont la même personne quand ils portent le même identifiant, pas avant. */
    private static boolean estLaMemePersonne(Utilisateur un, Utilisateur autre) {
        return un != null && autre != null && un.getId() != null && un.getId().equals(autre.getId());
    }

    /** La demande telle qu'un écran la lit, avec le droit d'écrire de celui qui la lit. */
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
                // `modifiable` dit « par moi », non « en général » : l'écran ouvre ses champs
                // sur cette seule foi, et un formulaire ouvert qui finit en 403 ment à qui le lit.
                !lettre.estGelee() && puisJeRediger(lettre.getDemande()),
                lettre.getDateGeneration(), lettre.getDateModification(),
                nomDu(lettre.getRedigePar()), profil(lettre.getRedigePar()));
    }

    /**
     * Celui dont la plume a écrit la lettre, pour la colonne de gauche.
     *
     * <p>C'est <b>le rédacteur enregistré</b>. Il n'y en a qu'un cas où il ne convient pas : quand
     * le supérieur hiérarchique a corrigé la lettre avant de la signer (Q-124), il devient le
     * dernier rédacteur et se retrouverait des <b>deux</b> côtés, ce qui ne dit plus rien. On
     * reprend alors le juriste qui a sollicité la constitution - c'est à lui que la lettre revient,
     * et sa correction par le SH reste tracée par ailleurs, à l'écran comme dans l'historique.
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

    /** Une personne et sa signature enregistrée - l'image peut manquer, le nom non. */
    private LettreConstitutionService.Signataire signataire(Utilisateur utilisateur) {
        if (utilisateur == null) {
            return null;
        }
        byte[] image = signatureService.pour(utilisateur.getId()).map(s -> s.image()).orElse(null);
        return new LettreConstitutionService.Signataire(image, nomDu(utilisateur));
    }

    /** L'identifiant, ou rien : une demande non encore validée n'a pas de signataire. */
    private static Long idDu(Utilisateur utilisateur) {
        return utilisateur == null ? null : utilisateur.getId();
    }

    /** Le nom du rédacteur, ou rien : le brouillon naît avec la demande, avant toute rédaction. */
    private static String nomDu(Utilisateur utilisateur) {
        if (utilisateur == null) {
            return null;
        }
        String prenom = utilisateur.getPrenom() == null ? "" : utilisateur.getPrenom() + " ";
        return (prenom + (utilisateur.getNom() == null ? "" : utilisateur.getNom())).trim();
    }

    /** Le code du profil du rédacteur - `JURISTE`, `SH` - pour que l'écran le nomme lui-même. */
    private static String profil(Utilisateur utilisateur) {
        if (utilisateur == null || utilisateur.getProfil() == null) {
            return null;
        }
        return utilisateur.getProfil().getCode();
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

        /*
         * Les signatures apposées sont celles des **deux personnes qui ont fait la lettre**, prises
         * à leur compte où elles seules les déposent (métier, 2026-10-06 puis 2026-10-07) - jamais
         * sur la foi du champ « signataire », qui est du texte libre : apposer une image sur un
         * texte libre permettrait de signer au nom d'autrui.
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

    /** La lettre générée à la validation, si elle a bien été archivée dans la GED. */
    private NotificationPrestataireService.PieceJointe lettreAJoindre(DemandeConstitution demande) {
        Document lettre = demande.getLettreDocument();
        if (lettre == null || lettre.getCheminStockage() == null) {
            return null;
        }
        return new NotificationPrestataireService.PieceJointe(lettre.getId(), lettre.getNomFichier(),
                "application/pdf", lettre.getCheminStockage());
    }

    /**
     * Notifie le prestataire constitué. L'alerte applicative reste réservée à un {@link Avocat}
     * disposant d'un compte (RG-INT-01) ; les canaux externes (Q-118), eux, visent les coordonnées de
     * la fiche et touchent donc aussi l'autre prestataire, qui n'était jusqu'ici jamais prévenu (Q-52).
     */
    private void notifierPrestataire(DemandeConstitution demande) {
        Long dossierId = demande.getDossier().getId();
        // D5 : la lettre part avec la notification, par courriel — le seul canal qui la délivre.
        notificationPrestataireService.notifier(EvenementPrestataire.CONSTITUTION_VALIDEE, demande.getPrestataire(),
                dossierId, "constitution-" + demande.getId(), lettreAJoindre(demande));
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
