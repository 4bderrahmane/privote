package org.krino.voting_system.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.krino.voting_system.entity.enums.ElectionPhase;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "elections",
        indexes = {
                @Index(name = "idx_elections_public_id", columnList = "public_id", unique = true),
                @Index(name = "idx_elections_contract_address", columnList = "contract_address", unique = true),
                @Index(name = "idx_elections_end_time", columnList = "end_time")
        }
)
public class Election
{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    /**
     * Stable backend/application identifier for the election.
     *
     * This is a good canonical value to use on the client side as the electionKey
     * for deriving per-election Semaphore identities.
     */
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    /**
     * On-chain deployed election contract address.
     * Nullable before deployment.
     */
    @Column(name = "contract_address", unique = true, length = 42)
    private String contractAddress;

    @PrePersist
    @PreUpdate
    void normalize()
    {
        if (publicId == null) publicId = UUID.randomUUID();
        if (contractAddress != null) contractAddress = contractAddress.toLowerCase();
    }

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    /**
     * Time at which voting started.
     * Nullable before the election enters voting phase.
     */
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ElectionPhase phase;

    /**
     * On-chain externalNullifier / scope used by the proof system.
     * Stored as decimal-compatible BigInteger.
     */
    @Column(name = "external_nullifier", nullable = false, precision = 78, scale = 0)
    private BigInteger externalNullifier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "coordinator_id",
            referencedColumnName = "id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_elections_coordinator")
    )
    private Citizen coordinator;

    /**
     * Public key used to encrypt ballots.
     * Stored as raw bytes.
     *
     * Make sure this matches the representation expected by the smart contract.
     * If the contract uses bytes32, then this value should be exactly 32 bytes.
     */
    @Column(name = "encryption_public_key", nullable = false, columnDefinition = "bytea")
    private byte[] encryptionPublicKey;

    /**
     * Material used later to open / decrypt ballots during tally.
     * This is intentionally generic and not named "private key", because the system
     * may evolve to threshold decryption or other tally-opening mechanisms.
     */
    @Column(name = "decryption_material", columnDefinition = "bytea")
    private byte[] decryptionMaterial;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Election election = (Election) o;
        return id != null && id.equals(election.id);
    }

    @Override
    public int hashCode()
    {
        return getClass().hashCode();
    }
}
