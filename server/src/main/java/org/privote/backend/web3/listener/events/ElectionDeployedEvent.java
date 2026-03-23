package org.privote.backend.web3.listener.events;

import java.math.BigInteger;
import java.util.UUID;

public record ElectionDeployedEvent(
        UUID uuid,
        BigInteger externalNullifier,
        String coordinator,
        String electionAddress,
        BigInteger endTimeSeconds,
        String txHash,
        BigInteger blockNumber,
        BigInteger logIndex
)
{
}