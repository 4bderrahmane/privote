package org.krino.voting_system.repository;

import org.krino.voting_system.entity.VoterCommitment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoterCommitmentRepository extends JpaRepository<VoterCommitment, Long>
{
    Optional<VoterCommitment> findByCitizenKeycloakIdAndElectionPublicId(UUID citizenKeycloakId, UUID electionPublicId);

    Optional<VoterCommitment> findByElectionPublicIdAndIdentityCommitment(UUID electionPublicId, String identityCommitment);

    List<VoterCommitment> findByElectionPublicId(UUID electionPublicId);
}
