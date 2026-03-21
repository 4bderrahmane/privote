package org.krino.voting_system.dto.election;

import lombok.Data;
import org.krino.voting_system.entity.enums.ElectionPhase;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

@Data
public class ElectionCreateDto
{
    private String title;

    private String description;

    /**
     * Optional. If null, the election can be considered to start immediately depending on the phase handling.
     */
    private Instant startTime;

    /**
     * Required.
     */
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
    private UUID coordinatorKeycloakId;

    /**
     * Required: election encryption public key (expected bytes32 on-chain).
     */
    private byte[] encryptionPublicKey;

}
