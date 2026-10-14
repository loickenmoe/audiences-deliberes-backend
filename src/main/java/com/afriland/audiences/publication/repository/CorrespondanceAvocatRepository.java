package com.afriland.audiences.publication.repository;

import java.util.List;

import com.afriland.audiences.publication.domain.CorrespondanceAvocat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CorrespondanceAvocatRepository extends JpaRepository<CorrespondanceAvocat, Long> {

    boolean existsByPublicationId(Long publicationId);

    /** Q-86 : la correspondance déjà transmise, affichée sur le détail de la publication. */
    Optional<CorrespondanceAvocat> findByPublicationId(Long publicationId);

    /**
     * Les correspondances d'une page de publications, en une seule lecture (QF-58).
     *
     * <p>Le portail de l'avocat en affiche dix par page : une lecture par publication ferait dix
     * requêtes là où une suffit, et c'est l'écran qu'il ouvre en premier.
     */
    List<CorrespondanceAvocat> findByPublicationIdIn(java.util.Collection<Long> publicationIds);
}
