package org.krino.voting_system.repository;

import org.krino.voting_system.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long>
{
    Optional<Candidate> findByPublicId(UUID publicId);

    List<Candidate> findByElectionPublicId(UUID electionPublicId);

    boolean existsByElectionPublicIdAndCitizenKeycloakId(UUID electionPublicId, UUID citizenPublicId);

    boolean existsByElectionPublicIdAndCitizenKeycloakIdAndPublicIdNot(UUID electionPublicId, UUID citizenPublicId, UUID publicId);
}
