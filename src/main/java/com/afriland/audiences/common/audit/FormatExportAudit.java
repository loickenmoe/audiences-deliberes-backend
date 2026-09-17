package com.afriland.audiences.common.audit;

/**
 * Formats d'export de l'audit (Q-113).
 *
 * <p><b>Les deux, jamais l'un ou l'autre</b> : ils ne servent pas à la même chose. Le PDF produit
 * une <b>pièce figée</b>, qu'on remet à un inspecteur ; l'Excel produit une <b>matière</b>, qu'on
 * trie, recoupe et recalcule. Retenir un seul format aurait obligé l'un des deux usages à se
 * contenter d'un outil qui ne lui convient pas.
 */
public enum FormatExportAudit {
    PDF("application/pdf", "pdf"),
    EXCEL("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

    private final String typeMime;
    private final String extension;

    FormatExportAudit(String typeMime, String extension) {
        this.typeMime = typeMime;
        this.extension = extension;
    }

    public String typeMime() {
        return typeMime;
    }

    public String extension() {
        return extension;
    }
}
