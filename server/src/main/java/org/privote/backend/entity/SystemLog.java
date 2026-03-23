package org.privote.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.privote.backend.entity.enums.SystemLogOutcome;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "system_logs",
        indexes = {
                @Index(name = "idx_system_logs_admin", columnList = "admin_id"),
                @Index(name = "idx_system_logs_created_at", columnList = "created_at"),
                @Index(name = "idx_system_logs_outcome", columnList = "outcome")
        }
)
public class SystemLog
{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private Citizen admin;

    /**
     * Human-readable action name, e.g.:
     * "CREATE_ELECTION", "START_ELECTION", "DELETE_CANDIDATE".
     */
    @Column(name = "action", nullable = false, length = 128)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 32)
    private SystemLogOutcome outcome;

    // Optional target type: "Election", "Candidate", "Citizen".
    @Column(name = "target_type", length = 64)
    private String targetType;

    // Optional target identifier as string so different entity id types can fit.
    @Column(name = "target_id", length = 128)
    private String targetId;

    // Optional extra details or error message.
    @Column(name = "details", columnDefinition = "text")
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SystemLog systemLog = (SystemLog) o;
        return id != null && id.equals(systemLog.id);
    }

    @Override
    public int hashCode()
    {
        return getClass().hashCode();
    }
}
