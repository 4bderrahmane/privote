package org.krino.voting_system.web3.listener.events;

import java.math.BigInteger;

public record MemberAddedEvent(
        String electionAddress,
        BigInteger groupId,
        BigInteger index,
        BigInteger identityCommitment,
        BigInteger merkleTreeRoot,
        String txHash,
        BigInteger blockNumber,
        BigInteger logIndex
)
{
}
