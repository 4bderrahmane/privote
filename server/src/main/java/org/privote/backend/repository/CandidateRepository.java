package org.privote.backend.repository;

import org.privote.backend.entity.Candidate;
import org.privote.backend.entity.enums.CandidateStatus;
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

    List<Candidate> findByElectionPublicIdAndStatus(UUID electionPublicId, CandidateStatus status);

    boolean existsByElectionPublicIdAndStatus(UUID electionPublicId, CandidateStatus status);

    boolean existsByElectionPublicIdAndCitizenKeycloakId(UUID electionPublicId, UUID citizenPublicId);

    boolean existsByElectionPublicIdAndCitizenKeycloakIdAndPublicIdNot(UUID electionPublicId, UUID citizenPublicId, UUID publicId);
}
