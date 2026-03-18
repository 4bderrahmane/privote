package org.krino.voting_system.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.krino.voting_system.entity.enums.CommitmentStatus;

import java.time.Instant;

@Entity
@Table(
        name = "voter_commitments",
        indexes = {
                @Index(name = "idx_voter_commitment_citizen", columnList = "citizen_id"),
                @Index(name = "idx_voter_commitment_election", columnList = "election_id"),
                @Index(name = "idx_voter_commitment_status", columnList = "status")
        },
        uniqueConstraints = {
                // One citizen should have at most one commitment record per election.
                @UniqueConstraint(
                        name = "uk_voter_commitment_citizen_election",
                        columnNames = {"citizen_id", "election_id"}
                ),

                // Commitment uniqueness should be scoped to the election.
                @UniqueConstraint(
                        name = "uk_voter_commitment_election_commitment",
                        columnNames = {"election_id", "identity_commitment"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class VoterCommitment
{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Real citizen in the authority/enrollment layer.
     * This link should be protected by access control, not nulled out in the DB.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "citizen_id", nullable = false)
    private Citizen citizen;

    /**
     * Election this commitment belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    /**
     * Election-specific Semaphore identity commitment.
     *
     * With the stronger identity model, this should be derived from:
     * - the user's master secret
     * - the canonical election key
     *
     * Stored as string because it is a large field element value.
     */
    @Column(name = "identity_commitment", nullable = false, length = 128)
    private String identityCommitment;

    /**
     * Optional Merkle leaf index once the commitment is inserted on-chain / in the tree.
     */
    @Column(name = "merkle_leaf_index")
    private Long merkleLeafIndex;

    /**
     * Blockchain transaction hash that inserted the commitment.
     *
     * Not unique: multiple commitments may be added in one batch transaction.
     */
    @Column(name = "transaction_hash", length = 66)
    private String transactionHash;

    /**
     * PENDING: Received from client, waiting to be sent on-chain.
     * ON_CHAIN: Successfully added to the Merkle tree / group.
     * FAILED: Registration transaction failed or was rejected.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CommitmentStatus status = CommitmentStatus.PENDING;

    @CreationTimestamp
    @Column(name = "registered_at", updatable = false, nullable = false)
    private Instant registeredAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VoterCommitment that = (VoterCommitment) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode()
    {
        return getClass().hashCode();
    }
}
