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
     * Search-as-you-type on <b>code</b> and <b>name</b>.
     *
     * <p>Partial and case-insensitive: {@code bona} finds Bonanjo, Bonabéri, Bonamoussadi and
     * Bonapriso. {@code motif} arrives already wrapped in percent signs and <b>escaped by the
     * service</b>, otherwise a percent typed by the user would return the whole list.
     *
     * <p>{@code actifSeulement} filters what is offered for input; the admin screen requests
     * everything, including closed branches.
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
