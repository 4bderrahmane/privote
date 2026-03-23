package org.privote.backend.web3.listener.events;

import java.math.BigInteger;

public record VoteAddedEvent(
        String electionAddress,
        byte[] ciphertextHash,
        BigInteger nullifier,
        byte[] ciphertext,
        String txHash,
        BigInteger blockNumber,
        BigInteger logIndex)
{

}
