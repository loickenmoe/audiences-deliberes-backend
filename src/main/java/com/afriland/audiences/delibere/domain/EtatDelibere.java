package com.afriland.audiences.delibere.domain;

/**
 * State of a deliberation, derived from its data and never stored:
 * <ul>
 *   <li>{@code EN_ATTENTE}: pending, result unknown; can be extended or reopened;</li>
 *   <li>{@code VIDE}: decision rendered, the step is "deliberation delivered";</li>
 *   <li>{@code RABATTU}: the judge reopened the proceedings before ruling (RG-DEL-07).</li>
 * </ul>
 */
public enum EtatDelibere {
    EN_ATTENTE,
    VIDE,
    RABATTU
}
