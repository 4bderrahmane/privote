package org.krino.voting_system.repository;

import org.krino.voting_system.entity.CitizenElectionParticipation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CitizenElectionParticipationRepository extends JpaRepository<CitizenElectionParticipation, Long>
{
    Optional<CitizenElectionParticipation> findByCitizenKeycloakIdAndElectionPublicId(UUID citizenKeycloakId, UUID electionPublicId);

    List<CitizenElectionParticipation> findByElectionPublicId(UUID electionPublicId);
}
