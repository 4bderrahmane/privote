package org.krino.voting_system.web3.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.krino.voting_system.web3.config.Web3jProperties;
import org.krino.voting_system.web3.contracts.ElectionFactory;
import org.krino.voting_system.web3.util.Web3Types;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

import java.io.IOException;
import java.math.BigInteger;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ElectionFactoryClient
{
    static final long LOCAL_HARDHAT_CHAIN_ID = 31337L;
    static final String LOCAL_HARDHAT_FACTORY_ADDRESS = "0x9fE46736679d2D9a65F0992F2272dE9f3c7fa6e0";

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

        TransactionReceipt receipt = factory.createElection(uuid16, endTimeSeconds, encryptionPubKey32).send();
        if (receipt == null)
        {
            throw new IllegalStateException("createElection returned no transaction receipt");
        }

        if (!receipt.isStatusOK())
        {
            throw new IllegalStateException(
                    "createElection transaction failed: txHash=" + receipt.getTransactionHash()
                            + ", status=" + receipt.getStatus()
            );
        }

        var events = factory.getElectionDeployedEvents(receipt);
        if (!events.isEmpty())
        {
            return normalizeAddress(events.get(0).election);
        }

        String deployedAddress = normalizeAddress(factory.electionByUuid(uuid16).send());
        if (!isZeroAddress(deployedAddress))
        {
            return deployedAddress;
        }

        throw new IllegalStateException(
                "ElectionDeployed event not found and electionByUuid returned zero address"
                        + ": txHash=" + receipt.getTransactionHash()
        );
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
        String factoryAddress = resolveFactoryAddress();

        return org.krino.voting_system.web3.contracts.ElectionFactory.load(
                factoryAddress,
                web3j,
                txManager,
                gasProvider
        );
    }

    String resolveFactoryAddress()
    {
        String configured = normalizeNullableAddress(props.getElectionFactoryAddress());
        if (configured != null && hasContractCode(configured))
        {
            return configured;
        }

        String fallback = localHardhatFallbackAddress();
        if (fallback != null && hasContractCode(fallback))
        {
            if (configured == null)
            {
                log.warn("Using local Hardhat ElectionFactory fallback at {}", fallback);
            } else
            {
                log.warn("Configured electionFactoryAddress {} has no contract code. Falling back to local Hardhat factory {}.", configured, fallback);
            }
            return fallback;
        }

        if (configured != null)
        {
            throw new IllegalStateException("Configured electionFactoryAddress has no contract code: " + configured);
        }

        throw new IllegalStateException("ElectionFactory address is not configured and no local Hardhat fallback contract was found");
    }

    private String localHardhatFallbackAddress()
    {
        if (props.getChainId() != LOCAL_HARDHAT_CHAIN_ID)
        {
            return null;
        }

        String clientAddress = props.getClientAddress();
        if (clientAddress == null)
        {
            return null;
        }

        String normalized = clientAddress.trim().toLowerCase();
        if (!normalized.equals("http://127.0.0.1:8545") && !normalized.equals("http://localhost:8545"))
        {
            return null;
        }

        String configured = normalizeNullableAddress(props.getElectionFactoryAddress());
        if (LOCAL_HARDHAT_FACTORY_ADDRESS.equals(configured))
        {
            return null;
        }

        return LOCAL_HARDHAT_FACTORY_ADDRESS;
    }

    private boolean hasContractCode(String address)
    {
        try
        {
            String code = web3j.ethGetCode(address, DefaultBlockParameterName.LATEST).send().getCode();
            return code != null && !code.isBlank() && !"0x".equalsIgnoreCase(code) && !"0x0".equalsIgnoreCase(code);
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Failed to verify electionFactoryAddress: " + address, ex);
        }
    }

    private static String normalizeNullableAddress(String address)
    {
        if (address == null || address.isBlank())
        {
            return null;
        }

        return normalizeAddress(address);
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

    private static boolean isZeroAddress(String address)
    {
        return "0x0000000000000000000000000000000000000000".equals(address);
    }
}
