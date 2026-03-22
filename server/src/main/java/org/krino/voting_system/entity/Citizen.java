package org.krino.voting_system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Builder
@Entity
@Table(
        name = "citizens",
        indexes = {
                @Index(name = "idx_citizen_keycloak", columnList = "keycloak_id"),
                @Index(name = "idx_citizen_cin", columnList = "cin"),
                @Index(name = "idx_citizen_email", columnList = "email")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Citizen
{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    @Column(name = "keycloak_id", nullable = false, unique = true, updatable = false)
    private UUID keycloakId;

    private String username;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    private String address;

    private String region;

    private String birthPlace;

    private LocalDate birthDate;

    @Column(name = "cin", nullable = false, unique = true)
    private String cin;

    @Column(nullable = false)
    private String email;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Builder.Default
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Builder.Default
    @Column(name = "is_eligible", nullable = false)
    private boolean isEligible = true;

    @Builder.Default
    @OneToMany(mappedBy = "citizen", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<VoterCommitment> voterCommitments = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "citizen", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Candidate> candidacies = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Citizen citizen = (Citizen) o;
        return keycloakId != null && keycloakId.equals(citizen.keycloakId);
    }

    @Override
    public int hashCode()
    {
        return getClass().hashCode();
    }
}
