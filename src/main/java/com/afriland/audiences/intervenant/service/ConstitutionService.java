package com.afriland.audiences.intervenant.service;

import com.afriland.audiences.alerte.domain.TypeAlerte;
import com.afriland.audiences.alerte.service.AlerteService;
import com.afriland.audiences.common.audit.HistoriqueActionService;
import com.afriland.audiences.common.error.ApiException;
import com.afriland.audiences.common.error.ErrorCodes;
import com.afriland.audiences.common.security.CurrentUserService;
import com.afriland.audiences.dossier.domain.Dossier;
import com.afriland.audiences.dossier.repository.DossierRepository;
import com.afriland.audiences.ged.domain.Document;
import com.afriland.audiences.ged.domain.FormatDocument;
import com.afriland.audiences.ged.repository.DocumentRepository;
import com.afriland.audiences.ged.service.MinioStorageService;
import com.afriland.audiences.intervenant.domain.DemandeConstitution;
import com.afriland.audiences.intervenant.domain.StatutConstitution;
import com.afriland.audiences.intervenant.mapper.DemandeConstitutionMapper;
import com.afriland.audiences.intervenant.repository.DemandeConstitutionRepository;
import com.afriland.audiences.intervenant.web.dto.CreerConstitutionRequest;
import com.afriland.audiences.intervenant.web.dto.DemandeConstitutionResponse;
import com.afriland.audiences.intervenant.web.dto.ValiderConstitutionRequest;
import com.afriland.audiences.referentiel.domain.Avocat;
import com.afriland.audiences.referentiel.domain.Intervenant;
import com.afriland.audiences.referentiel.domain.Utilisateur;
import com.afriland.audiences.referentiel.repository.IntervenantRepository;
import com.afriland.audiences.referentiel.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
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
    private final MinioStorageService minioStorageService;
    private final DocumentRepository documentRepository;
    private final DemandeConstitutionMapper demandeConstitutionMapper;

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

        historiqueActionService.enregistrerPour(demandeur, dossier.getId(), "Demande de constitution d'un prestataire",
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

        historiqueActionService.enregistrerPour(sh, demande.getDossier().getId(), "Décision sur une demande de constitution",
                Map.of("demandeId", demandeId, "decision", request.decision().name()));

        return demandeConstitutionMapper.toResponse(demande);
    }

    /**
     * M15 (Q-68) : file de travail « constitutions à signer » du SH — endpoint 🆕.
     * {@code statut=EN_ATTENTE} alimente son écran ; sans filtre, l'historique complet.
     */
    @Transactional(readOnly = true)
    public List<DemandeConstitutionResponse> lister(StatutConstitution statut) {
        var demandes = statut == null
                ? demandeConstitutionRepository.findByOrderByDateDemandeDesc()
                : demandeConstitutionRepository.findByStatutOrderByDateDemandeAsc(statut);
        return demandes.stream().map(demandeConstitutionMapper::toResponse).toList();
    }

    /** RG-INT-08 : la validation génère la lettre de constitution, stockée dans la GED (M8). */
    private void genererEtAttacherLettre(DemandeConstitution demande, Utilisateur sh) {
        byte[] pdf = lettreConstitutionService.generer(demande);
        String nomFichier = "lettre-constitution-" + demande.getId() + ".pdf";
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
     * Notifie le prestataire constitué s'il dispose d'un compte applicatif — seul un {@link Avocat}
     * (compte Keycloak, RG-INT-01) peut recevoir une alerte in-app. Un {@code AutrePrestataire}
     * n'a pas de compte et ne peut être notifié « par email » : aucune infrastructure SMTP n'est
     * fournie au projet (même limitation que Q-37) — non implémenté, documenté (Q-52).
     */
    private void notifierPrestataire(DemandeConstitution demande) {
        if (!(demande.getPrestataire() instanceof Avocat avocat)) {
            return;
        }
        utilisateurRepository.findByKeycloakId(avocat.getCompteKeycloak()).ifPresent(destinataire ->
                alerteService.declencher(TypeAlerte.CONSTITUTION_A_SIGNER, demande.getDossier().getId(), destinataire,
                        "Votre constitution sur le dossier " + demande.getDossier().getReference()
                                + " a été validée ; la lettre de constitution est disponible dans la GED du dossier."));
    }
}
