package org.krino.voting_system.repository;

import org.jspecify.annotations.NonNull;
import org.krino.voting_system.entity.Election;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ElectionRepository extends JpaRepository<Election, Long>
{
    Optional<Election> findByPublicId(UUID publicId);

    Optional<Election> findByContractAddressIgnoreCase(String contractAddress);

    List<Election> findByContractAddressIsNotNull();

    @NonNull List<Election> findAll();
}
