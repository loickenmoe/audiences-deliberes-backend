package com.afriland.audiences.dossier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class DossierAvocatId implements Serializable {

    @Column(name = "dossier_id")
    private Long dossierId;

    @Column(name = "avocat_id")
    private Long avocatId;
}
