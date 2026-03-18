package org.krino.voting_system.web3.listener.events;

import java.math.BigInteger;

public record ElectionStartedEvent(
        String electionAddress,
        String coordinator,
        BigInteger startTimeSeconds,
        BigInteger endTimeSeconds,
        String txHash,
        BigInteger blockNumber,
        BigInteger logIndex
)
{
}
