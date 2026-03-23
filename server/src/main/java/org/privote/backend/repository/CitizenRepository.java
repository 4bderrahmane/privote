package org.privote.backend.repository;

import org.privote.backend.entity.Citizen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CitizenRepository extends JpaRepository<Citizen, Long>
{
    Optional<Citizen> findByKeycloakId(UUID uuid);
    Optional<Citizen> findByKeycloakIdAndIsDeletedFalse(UUID uuid);

    Optional<Citizen> findByCin(String cin);
    Optional<Citizen> findByCinAndIsDeletedFalse(String cin);

    boolean existsByCinAndKeycloakIdNot(String cin, UUID keycloakId);

    List<Citizen> findAllByIsDeletedFalse();
}
