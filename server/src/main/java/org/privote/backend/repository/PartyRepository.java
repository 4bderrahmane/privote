package org.privote.backend.repository;

import org.privote.backend.entity.Party;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PartyRepository extends JpaRepository<Party, Long>
{
    Optional<Party> findByPublicId(UUID publicId);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndPublicIdNot(String name, UUID publicId);
}
