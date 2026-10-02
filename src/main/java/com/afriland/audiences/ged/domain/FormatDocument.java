package com.afriland.audiences.ged.domain;

/**
 * Formats acceptés (RG-GED-04, contrainte {@code chk_document_format}).
 *
 * <p>Le format est déduit de l'<b>extension</b> du fichier déposé, jamais du type MIME déclaré
 * (Q-46) : un nom de constante doit donc correspondre exactement à une extension, en majuscules.
 *
 * <p>Toute valeur ajoutée ici doit l'être aussi dans {@code chk_document_format} et dans le
 * paramètre {@code FORMATS_GED}, faute de quoi le fichier serait refusé par la base ou par la
 * configuration — deux refus que l'utilisateur ne saurait pas distinguer.
 */
public enum FormatDocument {
    PDF,
    PNG,
    JPG,
    TXT,
    XLSX,

    /** Word depuis 2007. Seul format Word que l'application sait <b>afficher</b> (2026-10-02). */
    DOCX,

    /**
     * Word 97-2003, binaire. Accepté au dépôt parce que la banque en détient encore, mais
     * <b>sans aperçu</b> : aucune bibliothèque de navigateur ne sait le rendre. L'écran l'annonce
     * et propose le téléchargement (arbitrage du métier, 2026-10-02).
     */
    DOC
}
