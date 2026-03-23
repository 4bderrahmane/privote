package org.privote.backend.repository;

import org.privote.backend.entity.Ballot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BallotRepository extends JpaRepository<Ballot, UUID>
{
    List<Ballot> findByElectionPublicId(UUID electionPublicId);

    Optional<Ballot> findByTransactionHash(String transactionHash);

    Optional<Ballot> findByElectionPublicIdAndNullifier(UUID electionPublicId, String nullifier);
}
