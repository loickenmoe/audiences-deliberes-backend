package com.afriland.audiences.delibere.domain;

/**
 * État d'un délibéré (Q-78), dérivé de ses données — jamais stocké :
 * <ul>
 *   <li>{@code EN_ATTENTE} : mis en délibéré, résultat inconnu ; prorogeable et rabattable ;</li>
 *   <li>{@code VIDE} : la décision est rendue (résultat connu), l'étape est « Délibéré vidé » ;</li>
 *   <li>{@code RABATTU} : le juge a rouvert les débats avant de statuer (RG-DEL-07).</li>
 * </ul>
 */
public enum EtatDelibere {
    EN_ATTENTE,
    VIDE,
    RABATTU
}
