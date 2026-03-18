package org.krino.voting_system.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "ballots",
        indexes = {
                @Index(name = "idx_ballot_election", columnList = "election_id"),
                @Index(name = "idx_ballot_nullifier", columnList = "nullifier"),
                @Index(name = "idx_ballot_ciphertext_hash", columnList = "ciphertext_hash"),
                @Index(name = "idx_ballot_transaction_hash", columnList = "transaction_hash")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ballot_election_nullifier", columnNames = {"election_id", "nullifier"}),
                @UniqueConstraint(name = "uk_ballot_transaction_hash", columnNames = {"transaction_hash"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Ballot
{

    @Id
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @Column(name = "ciphertext", nullable = false, columnDefinition = "bytea")
    private byte[] ciphertext;

    // keccak256(ciphertext), typically stored as a 0x-prefixed hex string.
    @Column(name = "ciphertext_hash", nullable = false, length = 66)
    private String ciphertextHash;

    @Column(name = "nullifier", nullable = false, length = 128)
    private String nullifier;

    @Column(name = "zk_proof", columnDefinition = "TEXT")
    private String zkProof;

    @Column(name = "transaction_hash", nullable = false, length = 66)
    private String transactionHash;

    @Column(name = "block_number")
    private Long blockNumber;

    // Decrypted/plaintext choice after tally.
    // Must remain null during voting if ballots are encrypted.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id")
    private Candidate candidate;

    @CreationTimestamp
    @Column(name = "cast_at", updatable = false, nullable = false)
    private Instant castAt;

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ballot ballot = (Ballot) o;
        return id != null && id.equals(ballot.id);
    }

    @Override
    public int hashCode()
    {
        return getClass().hashCode();
    }
}