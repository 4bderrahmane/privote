package org.privote.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.privote.backend.entity.Citizen;
import org.privote.backend.entity.Election;
import org.privote.backend.entity.enums.CandidateStatus;
import org.privote.backend.entity.enums.ElectionPhase;
import org.privote.backend.exception.BusinessConflictException;
import org.privote.backend.exception.RequestValidationException;
import org.privote.backend.repository.CandidateRepository;
import org.privote.backend.repository.ElectionRepository;
import org.privote.backend.web3.client.ElectionClient;
import org.privote.backend.web3.client.ElectionFactoryClient;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ElectionLifecycleServiceTest
{
    private final Map<UUID, Election> elections = new HashMap<>();
    private final Map<UUID, Boolean> activeCandidates = new HashMap<>();
    private ElectionLifecycleService electionLifecycleService;

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

    @BeforeEach
    void setUp()
    {
        elections.clear();
        activeCandidates.clear();
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
                candidateRepositoryStub(),
                new ElectionFactoryClient(null, null, null, null)
                {
                    @Override
                    public String getElectionAddress(UUID electionPublicId)
                    {
                        return "0x0000000000000000000000000000000000000000";
                    }

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
    void deployElectionRecoversExistingOnChainDeploymentAndClearsStaleLocalCollision()
    {
        UUID currentPublicId = UUID.randomUUID();
        UUID stalePublicId = UUID.randomUUID();
        String recoveredAddress = "0x75537828f2ce51be7289709686a69cbfdbb714f1";

        Election currentElection = election(currentPublicId, ElectionPhase.REGISTRATION, Instant.now().plusSeconds(3600));
        currentElection.setEncryptionPublicKey(new byte[32]);
        elections.put(currentPublicId, currentElection);

        Election staleElection = election(stalePublicId, ElectionPhase.VOTING, Instant.now().plusSeconds(7200));
        staleElection.setContractAddress(recoveredAddress);
        staleElection.setStartTime(Instant.now().minusSeconds(600));
        staleElection.setDecryptionMaterial(new byte[]{1, 2, 3});
        elections.put(stalePublicId, staleElection);

        electionLifecycleService = new ElectionLifecycleService(
                repositoryStub(),
                candidateRepositoryStub(),
                new ElectionFactoryClient(null, null, null, null)
                {
                    @Override
                    public String getElectionAddress(UUID electionPublicId)
                    {
                        if (currentPublicId.equals(electionPublicId))
                        {
                            return recoveredAddress;
                        }
                        if (stalePublicId.equals(electionPublicId))
                        {
                            return "0x0000000000000000000000000000000000000000";
                        }
                        return "0x0000000000000000000000000000000000000000";
                    }

                    @Override
                    public boolean isLocalHardhatEnvironment()
                    {
                        return true;
                    }

                    @Override
                    public String createElection(UUID electionPublicId, java.math.BigInteger endTimeSeconds, byte[] encryptionPubKey32)
                    {
                        throw new AssertionError("createElection should not be called when an orphaned on-chain deployment exists");
                    }
                },
                new ElectionClient(null, null, null)
        );

        Election deployed = electionLifecycleService.deployElection(currentPublicId);

        assertEquals(recoveredAddress, deployed.getContractAddress());
        assertEquals(ElectionPhase.REGISTRATION, staleElection.getPhase());
        assertEquals(null, staleElection.getContractAddress());
        assertEquals(null, staleElection.getStartTime());
        assertEquals(null, staleElection.getDecryptionMaterial());
    }

    @Test
    void startElectionMovesPhaseToVoting()
    {
        UUID publicId = UUID.randomUUID();
        Election election = election(publicId, ElectionPhase.REGISTRATION, Instant.now().plusSeconds(3600));
        election.setContractAddress("0xabc0000000000000000000000000000000000000");
        elections.put(publicId, election);
        activeCandidates.put(publicId, true);

        electionLifecycleService = new ElectionLifecycleService(
                repositoryStub(),
                candidateRepositoryStub(),
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
    void startElectionRequiresAtLeastOneActiveCandidate()
    {
        UUID publicId = UUID.randomUUID();
        Election election = election(publicId, ElectionPhase.REGISTRATION, Instant.now().plusSeconds(3600));
        election.setContractAddress("0xabc0000000000000000000000000000000000000");
        elections.put(publicId, election);

        electionLifecycleService = new ElectionLifecycleService(
                repositoryStub(),
                candidateRepositoryStub(),
                new ElectionFactoryClient(null, null, null, null),
                new ElectionClient(null, null, null)
        );

        BusinessConflictException ex = assertThrows(
                BusinessConflictException.class,
                () -> electionLifecycleService.startElection(publicId)
        );

        assertEquals("Election must have at least one ACTIVE candidate before voting can start", ex.getMessage());
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
                candidateRepositoryStub(),
                new ElectionFactoryClient(null, null, null, null),
                new ElectionClient(null, null, null)
        );

        RequestValidationException ex = assertThrows(
                RequestValidationException.class,
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
                    case "findByContractAddressIgnoreCase" -> elections.values().stream()
                            .filter(election -> election.getContractAddress() != null)
                            .filter(election -> election.getContractAddress().equalsIgnoreCase((String) args[0]))
                            .findFirst();
                    case "save", "saveAndFlush" ->
                    {
                        Election election = (Election) args[0];
                        elections.put(election.getPublicId(), election);
                        yield election;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "ElectionRepositoryStub";
                    default ->
                            throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
                }
        );
    }

    private CandidateRepository candidateRepositoryStub()
    {
        return (CandidateRepository) Proxy.newProxyInstance(
                CandidateRepository.class.getClassLoader(),
                new Class[]{CandidateRepository.class},
                (proxy, method, args) -> switch (method.getName())
                {
                    case "existsByElectionPublicIdAndStatus" ->
                            args[1] == CandidateStatus.ACTIVE && activeCandidates.getOrDefault((UUID) args[0], false);
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "CandidateRepositoryStub";
                    default ->
                            throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
                }
        );
    }
}
