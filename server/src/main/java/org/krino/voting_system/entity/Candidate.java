package org.krino.voting_system.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.krino.voting_system.entity.enums.CandidateStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "candidates",
        indexes = {
                @Index(name = "idx_candidates_public_id", columnList = "public_id", unique = true),
                @Index(name = "idx_candidates_election", columnList = "election_id"),
                @Index(name = "idx_candidates_status", columnList = "status"),
                @Index(name = "idx_candidates_party", columnList = "party_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_candidates_election_citizen", columnNames = {"election_id", "citizen_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Candidate
{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @PrePersist
    void prePersist()
    {
        if (publicId == null)
        {
            publicId = UUID.randomUUID();
        }
    }

    /**
     * The citizen who is running as a candidate in this election.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "citizen_id", nullable = false)
    private Citizen citizen;

    /**
     * The election this candidacy belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    /**
     * Optional party affiliation.
     * Nullable to support independent candidates.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id")
    private Party party;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CandidateStatus status = CandidateStatus.PENDING_APPROVAL;

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
        Candidate candidate = (Candidate) o;
        return id != null && id.equals(candidate.id);
    }

    @Override
    public int hashCode()
    {
        return getClass().hashCode();
    }
}
