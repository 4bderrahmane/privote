package org.krino.voting_system.web3.client;

import lombok.RequiredArgsConstructor;
import org.krino.voting_system.web3.config.Web3jProperties;
import org.krino.voting_system.web3.contracts.ElectionFactory;
import org.krino.voting_system.web3.util.Web3Types;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

import java.math.BigInteger;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ElectionFactoryClient
{
    private final Web3j web3j;
    private final TransactionManager txManager;
    private final ContractGasProvider gasProvider;
    private final Web3jProperties props;

    public String createElection(UUID electionPublicId, BigInteger endTimeSeconds, byte[] encryptionPubKey32) throws Exception
    {
        return createElection(Web3Types.uuidToBytes16(electionPublicId), endTimeSeconds, encryptionPubKey32);
    }

    public String createElection(byte[] uuid16, BigInteger endTimeSeconds, byte[] encryptionPubKey32) throws Exception
    {
        if (uuid16 == null || uuid16.length != 16)
        {
            throw new IllegalArgumentException("uuid must be 16 bytes");
        }
        if (endTimeSeconds == null || endTimeSeconds.signum() <= 0)
        {
            throw new IllegalArgumentException("endTimeSeconds must be positive");
        }
        if (encryptionPubKey32 == null || encryptionPubKey32.length != 32)
        {
            throw new IllegalArgumentException("encryptionPubKey32 must be 32 bytes");
        }

        var factory = loadFactory();

        var receipt = factory.createElection(uuid16, endTimeSeconds, encryptionPubKey32).send();

        var events = factory.getElectionDeployedEvents(receipt);
        if (events.isEmpty()) throw new IllegalStateException("ElectionDeployed event not found");

        return normalizeAddress(events.get(0).election);
    }

    public String getElectionAddress(UUID electionPublicId) throws Exception
    {
        return getElectionAddress(Web3Types.uuidToBytes16(electionPublicId));
    }

    public String getElectionAddress(byte[] uuid16) throws Exception
    {
        if (uuid16 == null || uuid16.length != 16)
        {
            throw new IllegalArgumentException("uuid must be 16 bytes");
        }

        return normalizeAddress(loadFactory().electionByUuid(uuid16).send());
    }

    private ElectionFactory loadFactory()
    {
        return org.krino.voting_system.web3.contracts.ElectionFactory.load(
                normalizeAddress(props.getElectionFactoryAddress()),
                web3j,
                txManager,
                gasProvider
        );
    }

    private static String normalizeAddress(String address)
    {
        if (address == null)
        {
            throw new IllegalArgumentException("address is required");
        }

        String value = address.trim();
        if (!value.startsWith("0x"))
        {
            value = "0x" + value;
        }
        if (value.length() != 42)
        {
            throw new IllegalArgumentException("address must be a 20-byte hex value");
        }
        return value.toLowerCase();
    }
}
