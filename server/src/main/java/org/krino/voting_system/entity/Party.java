package org.krino.voting_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "parties",
        indexes = {
                @Index(name = "idx_parties_public_id", columnList = "public_id", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Party
{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @PrePersist
    void prePersist()
    {
        if (publicId == null) publicId = UUID.randomUUID();
    }

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Party party = (Party) o;
        return id != null && id.equals(party.id);
    }

    @Override
    public int hashCode()
    {
        return getClass().hashCode();
    }
}