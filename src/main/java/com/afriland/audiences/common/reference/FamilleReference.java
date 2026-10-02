package com.afriland.audiences.common.reference;

/**
 * Les familles d'objets dont le système attribue lui-même la référence (métier, 2026-09-29).
 *
 * <p>Le préfixe est porté ici, à côté de la famille, pour qu'une référence ne puisse pas être
 * fabriquée ailleurs avec un autre préfixe. {@code DOS-2026-0001} n'a qu'une seule origine possible
 * dans le code.
 *
 * <p><b>N'y figurent que les références internes.</b> La référence de facture d'un avocat et la
 * référence d'incident de compte sont des identifiants <b>externes</b> — celui de sa facture à lui,
 * celui du SI bancaire. Les attribuer reviendrait à fabriquer une donnée qui existe ailleurs, et
 * dont le seul intérêt est justement de permettre le rapprochement avec cet ailleurs.
 */
public enum FamilleReference {

    /** Les dossiers contentieux : {@code DOS-2026-0001}. */
    DOSSIER("DOS"),

    /** Les clients : {@code CLI-2026-0001}. */
    CLIENT("CLI");

    private final String prefixe;

    FamilleReference(String prefixe) {
        this.prefixe = prefixe;
    }

    public String prefixe() {
        return prefixe;
    }
}
