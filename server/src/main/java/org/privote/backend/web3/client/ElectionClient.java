package org.privote.backend.web3.client;


import lombok.RequiredArgsConstructor;
import org.privote.backend.web3.contracts.Election;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

import java.math.BigInteger;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ElectionClient
{
    private final Web3j web3j;
    private final TransactionManager txManager;
    private final ContractGasProvider gasProvider;

    public TransactionReceipt addVoter(String electionAddress, BigInteger identityCommitment) throws Exception
    {
        var election = loadElection(electionAddress);
        return election.addVoter(identityCommitment).send();
    }

    public TransactionReceipt addVoters(String electionAddress, List<BigInteger> identityCommitments) throws Exception
    {
        return loadElection(electionAddress).addVoters(identityCommitments).send();
    }

    public TransactionReceipt startElection(String electionAddress) throws Exception
    {
        return loadElection(electionAddress).startElection().send();
    }

    public TransactionReceipt castVote(String electionAddress, byte[] ciphertext, BigInteger nullifier, List<BigInteger> proof) throws Exception
    {
        return loadElection(electionAddress).castVote(ciphertext, nullifier, proof).send();
    }

    public TransactionReceipt endElection(String electionAddress, byte[] decryptionMaterial) throws Exception
    {
        return loadElection(electionAddress).endElection(decryptionMaterial).send();
    }

    public boolean isNullifierUsed(String electionAddress, BigInteger nullifierHash) throws Exception
    {
        return loadElection(electionAddress).isNullifierUsed(nullifierHash).send();
    }

    public BigInteger getMerkleTreeRoot(String electionAddress, BigInteger groupId) throws Exception
    {
        return loadElection(electionAddress).getMerkleTreeRoot(groupId).send();
    }

    public BigInteger getMerkleTreeSize(String electionAddress, BigInteger groupId) throws Exception
    {
        return loadElection(electionAddress).getMerkleTreeSize(groupId).send();
    }

    public boolean hasMember(String electionAddress, BigInteger groupId, BigInteger identityCommitment) throws Exception
    {
        return loadElection(electionAddress).hasMember(groupId, identityCommitment).send();
    }

    public BigInteger indexOf(String electionAddress, BigInteger groupId, BigInteger identityCommitment) throws Exception
    {
        return loadElection(electionAddress).indexOf(groupId, identityCommitment).send();
    }

    private Election loadElection(String electionAddress)
    {
        return Election.load(
                normalizeAddress(electionAddress),
                web3j,
                txManager,
                gasProvider
        );
    }

    private static String normalizeAddress(String address)
    {
        if (address == null)
        {
            throw new IllegalArgumentException("electionAddress is required");
        }

        String value = address.trim();
        if (!value.startsWith("0x"))
        {
            value = "0x" + value;
        }
        if (value.length() != 42)
        {
            throw new IllegalArgumentException("electionAddress must be a 20-byte hex address");
        }
        return value.toLowerCase();
    }
}
