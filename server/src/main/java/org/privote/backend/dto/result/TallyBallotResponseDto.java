package org.privote.backend.dto.result;

import java.time.Instant;
import java.util.UUID;

public record TallyBallotResponseDto(
        UUID ballotId,
        String ciphertext,
        String ciphertextHash,
        String transactionHash,
        Long blockNumber,
        Instant castAt,
        UUID candidatePublicId
)
{
}
