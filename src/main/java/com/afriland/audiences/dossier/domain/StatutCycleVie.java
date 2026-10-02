package com.afriland.audiences.dossier.domain;

import java.util.List;

/** Statut de cycle de vie d'une étape de procédure (RG-DOS-04). */
public enum StatutCycleVie {
    OUVERTURE,
    EN_COURS,
    EN_DELIBERE,
    DELIBERE_VIDE,
    CLOTURE,
    ARCHIVE;

    /**
     * Les statuts d'une étape qui ne vit plus : plus d'audience à tenir, plus de recours à exercer.
     *
     * <p>Nommé ici plutôt que redéfini au cas par cas : {@code RepertoireAvocatsService} et
     * {@code ReportingService} en portent chacun leur copie, et une troisième — celle de la garde
     * jurisprudentielle — aurait fait diverger la notion le jour où un statut s'ajoute.
     */
    public static final List<StatutCycleVie> FERMES = List.of(CLOTURE, ARCHIVE);

    /** Cette étape est-elle terminée, au sens où plus rien n'y est attendu ? */
    public boolean estFerme() {
        return FERMES.contains(this);
    }
}
