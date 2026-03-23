package org.privote.backend.web3.listener.events;

import java.math.BigInteger;

public record ElectionEndedEvent(
        String electionAddress,
        String coordinator,
        byte[] decryptionMaterial,
        String txHash,
        BigInteger blockNumber,
        BigInteger logIndex
)
{
}
