package org.privote.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.privote.backend.entity.enums.CommitmentStatus;

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
                @UniqueConstraint(
                        name = "uk_voter_commitment_citizen_election",
                        columnNames = {"citizen_id", "election_id"}
                ),

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

    // Real citizen in the authority/enrollment layer.
    // This link should be protected by access control, not nulled out in the DB.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "citizen_id", nullable = false)
    private Citizen citizen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @Column(name = "identity_commitment", nullable = false, length = 128)
    private String identityCommitment;

    @Column(name = "merkle_leaf_index")
    private Long merkleLeafIndex;

    // Not unique: multiple commitments may be added in one batch transaction.
    @Column(name = "transaction_hash", length = 66)
    private String transactionHash;

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
