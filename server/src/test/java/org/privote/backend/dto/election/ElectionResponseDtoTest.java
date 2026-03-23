package org.privote.backend.dto.election;

import org.junit.jupiter.api.Test;
import org.privote.backend.entity.Election;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ElectionResponseDtoTest
{
    @Test
    void mapsExternalNullifierToDecimalString()
    {
        Election election = new Election();
        election.setExternalNullifier(new BigInteger("340282366920938463463374607431768211455"));

        ElectionResponseDto dto = ElectionResponseDto.fromEntity(election);

        assertEquals("340282366920938463463374607431768211455", dto.externalNullifier());
    }
}
