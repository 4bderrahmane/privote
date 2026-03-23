package org.krino.voting_system.dto.election;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.krino.voting_system.entity.enums.ElectionPhase;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

@Data
public class ElectionPatchDto
{
    @Pattern(regexp = ".*\\S.*", message = "title cannot be blank")
    private String title;
    private String description;
    private Instant startTime;
    private Instant endTime;
    private ElectionPhase phase;

    @Positive(message = "externalNullifier must be positive")
    private BigInteger externalNullifier;
    private UUID coordinatorKeycloakId;

    @Size(min = 1, message = "encryptionPublicKey cannot be empty")
    private byte[] encryptionPublicKey;
}
