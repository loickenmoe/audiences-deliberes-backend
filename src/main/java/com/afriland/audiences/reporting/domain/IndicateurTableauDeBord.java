package com.afriland.audiences.reporting.domain;

/**
 * Les blocs du tableau de bord que l'on peut ouvrir pour en voir le détail (Q-137).
 *
 * <p><b>Un indicateur par bloc affiché, et aucun de plus.</b> Le taux de succès du recouvrement est
 * absent : il a quitté l'écran le 2026-10-06 au profit des trois décomptes qu'il résumait (Q-132).
 * Un indicateur qui ne s'affiche plus ne se détaille pas.
 *
 * <p><b>Ce que cette énumération promet.</b> Pour chaque valeur, le détail rend <b>exactement</b> ce
 * que le compteur compte - même prédicat, même source. C'est la seule propriété qui compte
 * vraiment : afficher « 12 » et lister neuf lignes ferait douter des deux. Chaque méthode du service
 * est donc écrite en regard de la requête de comptage, et un test les confronte.
 */
public enum IndicateurTableauDeBord {

    /** Dossiers portant au moins une étape qui n'est ni clôturée ni archivée. */
    DOSSIERS_ACTIFS,

    /** Dossiers dont plus aucune étape n'est en cours (métier, 2026-10-06) - le complément exact. */
    AFFAIRES_VIDEES,

    /** Audiences à l'état planifié, toutes dates confondues. */
    AUDIENCES_PLANIFIEES,

    /** Demandes de frais ni payées ni rejetées : celles qui attendent encore une décision. */
    FRAIS_EN_ATTENTE,

    /**
     * Alertes de prorogation déclenchées et non traitées.
     *
     * <p><b>Une ligne par alerte, et non par délibéré.</b> C'est ce que le compteur compte : une
     * prorogation prévenant plusieurs destinataires produit plusieurs alertes, et le même dossier
     * peut donc apparaître deux fois. Regrouper par dossier donnerait une liste plus courte que le
     * nombre affiché.
     */
    PROROGATIONS_ALERTE,

    /**
     * Délibérés favorables.
     *
     * <p><b>⚠ Restreints aux dossiers de recouvrement</b>, comme le compteur - l'indicateur descend
     * du taux de succès du recouvrement, qui ne portait que sur eux. Le libellé de l'écran dit
     * « Litiges gagnés » sans le préciser : le détail, lui, le dit, et c'est la première occasion
     * qu'a la Direction de s'en apercevoir. Signalé pour arbitrage.
     */
    LITIGES_GAGNES,

    /** Délibérés défavorables, mêmes restrictions que {@link #LITIGES_GAGNES}. */
    LITIGES_PERDUS,

    /** Délibérés mixtes : ni gagnés ni perdus. Mêmes restrictions. */
    DECISIONS_MIXTES,

    /**
     * Adjudications portant une reprise de provision.
     *
     * <p><b>Le seul indicateur qui n'est pas un décompte</b> mais une somme d'argent. L'invariant
     * n'est donc pas « autant de lignes que le nombre affiché » mais « la somme des montants listés
     * fait le montant affiché », et c'est ce qu'un test vérifie.
     */
    PROVISIONS_REPRISES;

    /** Vrai pour le seul indicateur monétaire : l'écran y additionne, ailleurs il dénombre. */
    public boolean estUnMontant() {
        return this == PROVISIONS_REPRISES;
    }
}
