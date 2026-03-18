package org.krino.voting_system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.krino.voting_system.entity.enums.ParticipationStatus;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "citizen_election_participations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_citizen_election",
                columnNames = {"citizen_id", "election_id"}
        ),
        indexes = {
                @Index(name = "idx_participation_citizen", columnList = "citizen_id"),
                @Index(name = "idx_participation_election", columnList = "election_id"),
                @Index(name = "idx_participation_status", columnList = "status")
        }
)
public class CitizenElectionParticipation
{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The real citizen in the authority/enrollment layer.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "citizen_id", nullable = false)
    private Citizen citizen;

    /**
     * The election this participation record belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    /**
     * Election-specific participation / registration status.
     *
     * Be careful with statuses like VOTED or BALLOT_CAST:
     * those can weaken the privacy boundary if they are derived from
     * anonymous voting-layer activity.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ParticipationStatus status;

    /**
     * Timestamp for when the citizen was registered / enrolled in this election.
     */
    @CreationTimestamp
    @Column(name = "registered_at", updatable = false)
    private Instant registeredAt;

    /**
     * Optional generic last-update timestamp for workflow tracking.
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CitizenElectionParticipation that = (CitizenElectionParticipation) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode()
    {
        return getClass().hashCode();
    }
}
