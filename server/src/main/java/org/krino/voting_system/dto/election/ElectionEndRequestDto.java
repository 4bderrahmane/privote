package org.krino.voting_system.dto.election;

import lombok.Data;

@Data
public class ElectionEndRequestDto
{
    private byte[] decryptionMaterial;
}
