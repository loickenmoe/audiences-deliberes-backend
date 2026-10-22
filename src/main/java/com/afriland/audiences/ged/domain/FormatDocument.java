package com.afriland.audiences.ged.domain;

/**
 * Accepted formats (RG-GED-04, constraint {@code chk_document_format}).
 *
 * <p>The format is derived from the file <b>extension</b>, never from the declared MIME type:
 * each constant name must therefore match an extension exactly, in upper case.
 *
 * <p>Any value added here must also be added to {@code chk_document_format} and to the
 * {@code FORMATS_GED} parameter, otherwise the file would be rejected by the database or by the
 * configuration, two rejections the user could not tell apart.
 */
public enum FormatDocument {
    PDF,
    PNG,
    JPG,
    TXT,
    XLSX,

    /** Word 2007 and later. The only Word format the application can <b>preview</b>. */
    DOCX,

    /**
     * Word 97-2003, binary. Accepted on upload because the bank still holds such files, but
     * <b>without preview</b>: no browser library can render it. The UI says so and offers a download.
     */
    DOC
}
