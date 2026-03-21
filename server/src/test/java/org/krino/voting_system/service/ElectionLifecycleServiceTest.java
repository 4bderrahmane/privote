package org.krino.voting_system.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.krino.voting_system.entity.Citizen;
import org.krino.voting_system.entity.Election;
import org.krino.voting_system.entity.enums.ElectionPhase;
import org.krino.voting_system.repository.ElectionRepository;
import org.krino.voting_system.web3.client.ElectionClient;
import org.krino.voting_system.web3.client.ElectionFactoryClient;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ElectionLifecycleServiceTest
{
    private final Map<UUID, Election> elections = new HashMap<>();
    private ElectionLifecycleService electionLifecycleService;

    @BeforeEach
    void setUp()
    {
        elections.clear();
    }

    @Test
    void deployElectionPersistsContractAddressAndCanonicalExternalNullifier()
    {
        UUID publicId = UUID.randomUUID();
        Election election = election(publicId, ElectionPhase.REGISTRATION, Instant.now().plusSeconds(3600));
        election.setEncryptionPublicKey(new byte[32]);
        elections.put(publicId, election);

        electionLifecycleService = new ElectionLifecycleService(
                repositoryStub(),
                new ElectionFactoryClient(null, null, null, null)
                {
                    @Override
                    public String createElection(UUID electionPublicId, java.math.BigInteger endTimeSeconds, byte[] encryptionPubKey32)
                    {
                        assertEquals(publicId, electionPublicId);
                        assertEquals(32, encryptionPubKey32.length);
                        return "0xabc0000000000000000000000000000000000000";
                    }
                },
                new ElectionClient(null, null, null)
        );

        Election deployed = electionLifecycleService.deployElection(publicId);

        assertEquals("0xabc0000000000000000000000000000000000000", deployed.getContractAddress());
        assertEquals(ElectionService.deriveExternalNullifier(publicId), deployed.getExternalNullifier());
    }

    @Test
    void startElectionMovesPhaseToVoting()
    {
        UUID publicId = UUID.randomUUID();
        Election election = election(publicId, ElectionPhase.REGISTRATION, Instant.now().plusSeconds(3600));
        election.setContractAddress("0xabc0000000000000000000000000000000000000");
        elections.put(publicId, election);

        electionLifecycleService = new ElectionLifecycleService(
                repositoryStub(),
                new ElectionFactoryClient(null, null, null, null),
                new ElectionClient(null, null, null)
                {
                    @Override
                    public org.web3j.protocol.core.methods.response.TransactionReceipt startElection(String electionAddress)
                    {
                        assertEquals("0xabc0000000000000000000000000000000000000", electionAddress);
                        return new org.web3j.protocol.core.methods.response.TransactionReceipt();
                    }
                }
        );

        Election started = electionLifecycleService.startElection(publicId);

        assertEquals(ElectionPhase.VOTING, started.getPhase());
        assertNotNull(started.getStartTime());
    }

    @Test
    void endElectionRequiresDecryptionMaterial()
    {
        UUID publicId = UUID.randomUUID();
        Election election = election(publicId, ElectionPhase.VOTING, Instant.now().minusSeconds(10));
        election.setContractAddress("0xabc0000000000000000000000000000000000000");
        elections.put(publicId, election);

        electionLifecycleService = new ElectionLifecycleService(
                repositoryStub(),
                new ElectionFactoryClient(null, null, null, null),
                new ElectionClient(null, null, null)
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> electionLifecycleService.endElection(publicId, null)
        );

        assertEquals("Election decryptionMaterial is required before ending the election", ex.getMessage());
    }

    private ElectionRepository repositoryStub()
    {
        return (ElectionRepository) Proxy.newProxyInstance(
                ElectionRepository.class.getClassLoader(),
                new Class[]{ElectionRepository.class},
                (proxy, method, args) -> switch (method.getName())
                {
                    case "findByPublicId" -> Optional.ofNullable(elections.get((UUID) args[0]));
                    case "save" ->
                    {
                        Election election = (Election) args[0];
                        elections.put(election.getPublicId(), election);
                        yield election;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "ElectionRepositoryStub";
                    default -> throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
                }
        );
    }

    private static Election election(UUID publicId, ElectionPhase phase, Instant endTime)
    {
        Election election = new Election();
        election.setPublicId(publicId);
        election.setPhase(phase);
        election.setEndTime(endTime);
        election.setCoordinator(Citizen.builder().keycloakId(UUID.randomUUID()).build());
        election.setTitle("Test election");
        return election;
    }
}
