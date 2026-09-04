package com.afriland.audiences.referentiel.repository;

import com.afriland.audiences.referentiel.domain.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByReference(String reference);

    Page<Client> findByNomContainingIgnoreCase(String nom, Pageable pageable);
}
