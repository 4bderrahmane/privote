package org.krino.voting_system.dto.election;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.krino.voting_system.entity.enums.ElectionPhase;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

@Data
public class ElectionCreateDto
{
    @NotBlank(message = "title is required")
    private String title;

    private String description;

    /**
     * Optional. If null, the election can be considered to start immediately depending on the phase handling.
     */
    private Instant startTime;

    /**
     * Required.
     */
    @NotNull(message = "endTime is required")
    private Instant endTime;

    /**
     * Optional. If null, defaults to REGISTRATION.
     */
    private ElectionPhase phase;

    /**
     * Optional. If null, service generates one.
     */
    private BigInteger externalNullifier;

    /**
     * Required: citizen (coordinator) identifier.
     * We use keycloakId because it is unique and already used across the codebase.
     */
    @NotNull(message = "coordinatorKeycloakId is required")
    private UUID coordinatorKeycloakId;

    /**
     * Required: election encryption public key (expected bytes32 on-chain).
     */
    @NotNull(message = "encryptionPublicKey is required")
    @Size(min = 1, message = "encryptionPublicKey is required")
    private byte[] encryptionPublicKey;

}
