package com.afriland.audiences.referentiel.repository;

import com.afriland.audiences.referentiel.domain.Agence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgenceRepository extends JpaRepository<Agence, Long> {

    Optional<Agence> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndActifTrue(String code);

    /**
     * Recherche au fil de la frappe, sur le <b>code</b> et sur le <b>nom</b>.
     *
     * <p>Le métier l'a décrite ainsi : « si on commence par entrer le nom d'une agence, l'app nous
     * renvoie progressivement les agences avec ces noms ». Chercher aussi sur le code sert ceux qui
     * le connaissent par cœur — pour un gestionnaire, taper {@code 00002} va plus vite que
     * « Bonanjo ».
     *
     * <p>Recherche <b>partielle et insensible à la casse</b>, comme la recherche jurisprudentielle
     * (Q-89) : {@code bona} trouve Bonanjo, Bonabéri, Bonamoussadi et Bonapriso. Le {@code motif}
     * arrive déjà encadré de pourcents et <b>échappé par le service</b> — sans quoi un pourcent
     * saisi par l'utilisateur ramènerait tout le référentiel.
     *
     * <p>{@code actifSeulement} filtre ce qui se propose à la saisie ; l'écran d'administration,
     * lui, demande tout, y compris les agences fermées.
     */
    @Query("""
            select a from Agence a
             where (:actifSeulement = false or a.actif = true)
               and (:motif is null
                    or lower(a.code) like :motif escape '!'
                    or lower(a.nom) like :motif escape '!')
             order by a.code asc
            """)
    Page<Agence> rechercher(@Param("motif") String motif,
                            @Param("actifSeulement") boolean actifSeulement,
                            Pageable pageable);
}
