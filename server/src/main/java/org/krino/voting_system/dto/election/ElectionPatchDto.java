package org.krino.voting_system.dto.election;

import lombok.Data;
import org.krino.voting_system.entity.enums.ElectionPhase;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

@Data
public class ElectionPatchDto
{
    private String title;
    private String description;
    private Instant startTime;
    private Instant endTime;
    private ElectionPhase phase;
    private BigInteger externalNullifier;
    private UUID coordinatorKeycloakId;
    private byte[] encryptionPublicKey;
}
