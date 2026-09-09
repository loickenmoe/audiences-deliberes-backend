package com.afriland.audiences.common.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleApiException_returnsExactStatusCodeAndDetails() {
        ApiException ex = ApiException.conflict(ErrorCodes.REFERENCE_DOSSIER_DUPLIQUEE, "Référence déjà utilisée");

        ResponseEntity<ErrorResponse> response = handler.handleApiException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCodes.REFERENCE_DOSSIER_DUPLIQUEE);
        assertThat(response.getBody().message()).isEqualTo("Référence déjà utilisée");
        assertThat(response.getBody().details()).isNull();
    }

    @Test
    void handleApiException_nommeLeChampFautifQuandIlEstConnu() {
        ApiException ex = ApiException.conflictSurChamp(ErrorCodes.CONFLIT,
                "La référence client 'CLI-001' est déjà utilisée", "reference");

        ResponseEntity<ErrorResponse> response = handler.handleApiException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        // Sans ce champ, un client recevant ERR-CONFLICT devrait deviner la cible depuis l'endpoint.
        assertThat(response.getBody().champ()).isEqualTo("reference");
    }

    @Test
    void handleApiException_laisseLeChampNulPourUnConflitDEtat() {
        ApiException ex = ApiException.conflict(ErrorCodes.CONFLIT, "Cette demande a déjà été traitée");

        ResponseEntity<ErrorResponse> response = handler.handleApiException(ex);

        assertThat(response.getBody()).isNotNull();
        // Un conflit d'état ne vise aucun champ : mieux vaut ne rien dire que de désigner au hasard.
        assertThat(response.getBody().champ()).isNull();
    }

    @Test
    void handleApiException_propagatesDetailsWhenProvided() {
        ApiException ex = new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.METADONNEES_MANQUANTES,
                "Champs manquants", "reference, nature");

        ResponseEntity<ErrorResponse> response = handler.handleApiException(ex);

        assertThat(response.getBody().details()).isEqualTo("reference, nature");
    }

    @Test
    void handleAccessDenied_returns403WithGenericCode() {
        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(new AccessDeniedException("nope"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo(ErrorCodes.DROITS_INSUFFISANTS);
    }

    @Test
    void handleConstraintViolation_joinsAllViolationMessages() {
        ConstraintViolation<?> violation1 = mockViolation("montant", "doit être positif");
        ConstraintViolation<?> violation2 = mockViolation("referenceFacture", "ne doit pas être vide");
        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation1, violation2));

        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ErrorCodes.REQUETE_INVALIDE);
        assertThat(response.getBody().details())
                .contains("montant: doit être positif")
                .contains("referenceFacture: ne doit pas être vide");
    }

    @Test
    void handleUnexpected_neverLeaksExceptionMessageToClient() {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(new RuntimeException("secret interne"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo(ErrorCodes.ERREUR_INTERNE);
        assertThat(response.getBody().message()).doesNotContain("secret interne");
    }

    @Test
    void handleTypeMismatch_includesParameterNameAndValue() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("categorie");
        when(ex.getValue()).thenReturn("INVALIDE");

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("categorie");
        assertThat(response.getBody().details()).isEqualTo("INVALIDE");
    }

    @Test
    void formatFieldError_combinesFieldAndMessage() {
        FieldError fieldError = new FieldError("dossier", "reference", "ne doit pas être vide");

        String formatted = handler.formatFieldError(fieldError);

        assertThat(formatted).isEqualTo("reference: ne doit pas être vide");
    }

    private ConstraintViolation<?> mockViolation(String property, String message) {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn(property);
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn(message);
        return violation;
    }
}
