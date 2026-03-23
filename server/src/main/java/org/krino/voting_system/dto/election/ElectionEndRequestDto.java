package org.krino.voting_system.dto.election;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ElectionEndRequestDto
{
    @NotNull(message = "decryptionMaterial is required")
    @Size(min = 1, message = "decryptionMaterial is required")
    private byte[] decryptionMaterial;
}
