package com.afriland.audiences.referentiel.repository;

import com.afriland.audiences.referentiel.domain.Profil;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProfilRepository extends JpaRepository<Profil, Long> {

    Optional<Profil> findByCode(String code);
}
