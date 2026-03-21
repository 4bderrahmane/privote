package org.krino.voting_system.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.krino.voting_system.dto.election.VoterRegistrationRequestDto;
import org.krino.voting_system.dto.election.VoterRegistrationResponseDto;
import org.krino.voting_system.entity.Citizen;
import org.krino.voting_system.entity.CitizenElectionParticipation;
import org.krino.voting_system.entity.Election;
import org.krino.voting_system.entity.VoterCommitment;
import org.krino.voting_system.entity.enums.CommitmentStatus;
import org.krino.voting_system.entity.enums.ElectionPhase;
import org.krino.voting_system.entity.enums.ParticipationStatus;
import org.krino.voting_system.repository.CitizenElectionParticipationRepository;
import org.krino.voting_system.repository.CitizenRepository;
import org.krino.voting_system.repository.ElectionRepository;
import org.krino.voting_system.repository.VoterCommitmentRepository;
import org.krino.voting_system.web3.client.ElectionClient;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoterRegistrationServiceTest
{
    private final Map<UUID, Election> elections = new HashMap<>();
    private final Map<UUID, Citizen> citizens = new HashMap<>();
    private final Map<String, VoterCommitment> commitments = new HashMap<>();
    private final Map<String, CitizenElectionParticipation> participations = new HashMap<>();

    @BeforeEach
    void setUp()
    {
        elections.clear();
        citizens.clear();
        commitments.clear();
        participations.clear();
    }

    @Test
    void registerMyCommitmentEnrollsCitizenOnChainAndPersistsStatuses()
    {
        UUID electionPublicId = UUID.randomUUID();
        UUID citizenKeycloakId = UUID.randomUUID();
        elections.put(electionPublicId, election(electionPublicId));
        citizens.put(citizenKeycloakId, citizen(citizenKeycloakId, true));

        VoterRegistrationService service = new VoterRegistrationService(
                electionRepositoryStub(),
                citizenRepositoryStub(),
                participationRepositoryStub(),
                voterCommitmentRepositoryStub(),
                new ElectionClient(null, null, null)
                {
                    @Override
                    public boolean hasMember(String electionAddress, BigInteger groupId, BigInteger identityCommitment)
                    {
                        assertEquals(new BigInteger("123456"), identityCommitment);
                        return false;
                    }

                    @Override
                    public TransactionReceipt addVoter(String electionAddress, BigInteger identityCommitment)
                    {
                        TransactionReceipt receipt = new TransactionReceipt();
                        receipt.setTransactionHash("0xtx123");
                        return receipt;
                    }

                    @Override
                    public BigInteger indexOf(String electionAddress, BigInteger groupId, BigInteger identityCommitment)
                    {
                        return BigInteger.valueOf(7);
                    }
                }
        );

        VoterRegistrationResponseDto response = service.registerMyCommitment(
                electionPublicId,
                citizenKeycloakId,
                request("123456")
        );

        assertEquals(ParticipationStatus.REGISTERED, response.participationStatus());
        assertEquals(CommitmentStatus.ON_CHAIN, response.commitmentStatus());
        assertEquals("123456", response.identityCommitment());
        assertEquals(7L, response.merkleLeafIndex());
        assertEquals("0xtx123", response.transactionHash());

        VoterCommitment storedCommitment = commitments.get(commitmentKey(citizenKeycloakId, electionPublicId));
        assertEquals(CommitmentStatus.ON_CHAIN, storedCommitment.getStatus());
        assertEquals(7L, storedCommitment.getMerkleLeafIndex());

        CitizenElectionParticipation storedParticipation = participations.get(commitmentKey(citizenKeycloakId, electionPublicId));
        assertEquals(ParticipationStatus.REGISTERED, storedParticipation.getStatus());
    }

    @Test
    void registerMyCommitmentRecoversExistingOnChainMemberWithoutSendingTransaction()
    {
        UUID electionPublicId = UUID.randomUUID();
        UUID citizenKeycloakId = UUID.randomUUID();
        elections.put(electionPublicId, election(electionPublicId));
        citizens.put(citizenKeycloakId, citizen(citizenKeycloakId, true));

        AtomicBoolean addVoterCalled = new AtomicBoolean(false);

        VoterRegistrationService service = new VoterRegistrationService(
                electionRepositoryStub(),
                citizenRepositoryStub(),
                participationRepositoryStub(),
                voterCommitmentRepositoryStub(),
                new ElectionClient(null, null, null)
                {
                    @Override
                    public boolean hasMember(String electionAddress, BigInteger groupId, BigInteger identityCommitment)
                    {
                        return true;
                    }

                    @Override
                    public TransactionReceipt addVoter(String electionAddress, BigInteger identityCommitment)
                    {
                        addVoterCalled.set(true);
                        return new TransactionReceipt();
                    }

                    @Override
                    public BigInteger indexOf(String electionAddress, BigInteger groupId, BigInteger identityCommitment)
                    {
                        return BigInteger.valueOf(3);
                    }
                }
        );

        VoterRegistrationResponseDto response = service.registerMyCommitment(
                electionPublicId,
                citizenKeycloakId,
                request("555")
        );

        assertFalse(addVoterCalled.get());
        assertEquals(CommitmentStatus.ON_CHAIN, response.commitmentStatus());
        assertEquals(3L, response.merkleLeafIndex());
    }

    @Test
    void registerMyCommitmentRejectsIneligibleCitizen()
    {
        UUID electionPublicId = UUID.randomUUID();
        UUID citizenKeycloakId = UUID.randomUUID();
        elections.put(electionPublicId, election(electionPublicId));
        citizens.put(citizenKeycloakId, citizen(citizenKeycloakId, false));

        VoterRegistrationService service = new VoterRegistrationService(
                electionRepositoryStub(),
                citizenRepositoryStub(),
                participationRepositoryStub(),
                voterCommitmentRepositoryStub(),
                new ElectionClient(null, null, null)
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.registerMyCommitment(electionPublicId, citizenKeycloakId, request("123"))
        );

        assertEquals("Citizen is not eligible to register for elections", ex.getMessage());
        assertTrue(commitments.isEmpty());
        assertTrue(participations.isEmpty());
    }

    @Test
    void registerMyCommitmentMarksCommitmentFailedWhenChainEnrollmentThrows()
    {
        UUID electionPublicId = UUID.randomUUID();
        UUID citizenKeycloakId = UUID.randomUUID();
        elections.put(electionPublicId, election(electionPublicId));
        citizens.put(citizenKeycloakId, citizen(citizenKeycloakId, true));

        VoterRegistrationService service = new VoterRegistrationService(
                electionRepositoryStub(),
                citizenRepositoryStub(),
                participationRepositoryStub(),
                voterCommitmentRepositoryStub(),
                new ElectionClient(null, null, null)
                {
                    @Override
                    public boolean hasMember(String electionAddress, BigInteger groupId, BigInteger identityCommitment) throws Exception
                    {
                        throw new IllegalStateException("rpc unavailable");
                    }
                }
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.registerMyCommitment(electionPublicId, citizenKeycloakId, request("777"))
        );

        assertEquals("Failed to enroll voter commitment on chain", ex.getMessage());

        VoterCommitment commitment = commitments.get(commitmentKey(citizenKeycloakId, electionPublicId));
        assertEquals(CommitmentStatus.FAILED, commitment.getStatus());

        CitizenElectionParticipation participation = participations.get(commitmentKey(citizenKeycloakId, electionPublicId));
        assertEquals(ParticipationStatus.REGISTERED, participation.getStatus());
    }

    private ElectionRepository electionRepositoryStub()
    {
        return (ElectionRepository) Proxy.newProxyInstance(
                ElectionRepository.class.getClassLoader(),
                new Class[]{ElectionRepository.class},
                (proxy, method, args) -> switch (method.getName())
                {
                    case "findByPublicId" -> Optional.ofNullable(elections.get((UUID) args[0]));
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "ElectionRepositoryStub";
                    default -> throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
                }
        );
    }

    private CitizenRepository citizenRepositoryStub()
    {
        return (CitizenRepository) Proxy.newProxyInstance(
                CitizenRepository.class.getClassLoader(),
                new Class[]{CitizenRepository.class},
                (proxy, method, args) -> switch (method.getName())
                {
                    case "findByKeycloakIdAndIsDeletedFalse" -> Optional.ofNullable(citizens.get((UUID) args[0]));
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "CitizenRepositoryStub";
                    default -> throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
                }
        );
    }

    private CitizenElectionParticipationRepository participationRepositoryStub()
    {
        return (CitizenElectionParticipationRepository) Proxy.newProxyInstance(
                CitizenElectionParticipationRepository.class.getClassLoader(),
                new Class[]{CitizenElectionParticipationRepository.class},
                (proxy, method, args) -> switch (method.getName())
                {
                    case "findByCitizenKeycloakIdAndElectionPublicId" ->
                            Optional.ofNullable(participations.get(commitmentKey((UUID) args[0], (UUID) args[1])));
                    case "findByElectionPublicId" ->
                    {
                        UUID electionPublicId = (UUID) args[0];
                        List<CitizenElectionParticipation> results = new ArrayList<>();
                        for (CitizenElectionParticipation participation : participations.values())
                        {
                            if (participation.getElection().getPublicId().equals(electionPublicId))
                            {
                                results.add(participation);
                            }
                        }
                        yield results;
                    }
                    case "save" ->
                    {
                        CitizenElectionParticipation participation = (CitizenElectionParticipation) args[0];
                        participations.put(commitmentKey(
                                participation.getCitizen().getKeycloakId(),
                                participation.getElection().getPublicId()
                        ), participation);
                        yield participation;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "CitizenElectionParticipationRepositoryStub";
                    default -> throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
                }
        );
    }

    private VoterCommitmentRepository voterCommitmentRepositoryStub()
    {
        return (VoterCommitmentRepository) Proxy.newProxyInstance(
                VoterCommitmentRepository.class.getClassLoader(),
                new Class[]{VoterCommitmentRepository.class},
                (proxy, method, args) -> switch (method.getName())
                {
                    case "findByCitizenKeycloakIdAndElectionPublicId" ->
                            Optional.ofNullable(commitments.get(commitmentKey((UUID) args[0], (UUID) args[1])));
                    case "findByElectionPublicIdAndIdentityCommitment" ->
                    {
                        UUID electionPublicId = (UUID) args[0];
                        String identityCommitment = (String) args[1];
                        yield commitments.values().stream()
                                .filter(commitment -> commitment.getElection().getPublicId().equals(electionPublicId))
                                .filter(commitment -> identityCommitment.equals(commitment.getIdentityCommitment()))
                                .findFirst();
                    }
                    case "findByElectionPublicId" ->
                    {
                        UUID electionPublicId = (UUID) args[0];
                        yield commitments.values().stream()
                                .filter(commitment -> commitment.getElection().getPublicId().equals(electionPublicId))
                                .toList();
                    }
                    case "save" ->
                    {
                        VoterCommitment commitment = (VoterCommitment) args[0];
                        commitments.put(commitmentKey(
                                commitment.getCitizen().getKeycloakId(),
                                commitment.getElection().getPublicId()
                        ), commitment);
                        yield commitment;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "VoterCommitmentRepositoryStub";
                    default -> throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
                }
        );
    }

    private static VoterRegistrationRequestDto request(String identityCommitment)
    {
        VoterRegistrationRequestDto request = new VoterRegistrationRequestDto();
        request.setIdentityCommitment(identityCommitment);
        return request;
    }

    private static Election election(UUID publicId)
    {
        Election election = new Election();
        election.setPublicId(publicId);
        election.setTitle("Test election");
        election.setPhase(ElectionPhase.REGISTRATION);
        election.setContractAddress("0xabc0000000000000000000000000000000000000");
        election.setEndTime(Instant.now().plusSeconds(3_600));
        election.setExternalNullifier(BigInteger.valueOf(42));
        election.setEncryptionPublicKey(new byte[32]);
        election.setCoordinator(Citizen.builder().keycloakId(UUID.randomUUID()).build());
        return election;
    }

    private static Citizen citizen(UUID keycloakId, boolean eligible)
    {
        return Citizen.builder()
                .keycloakId(keycloakId)
                .firstName("A")
                .lastName("B")
                .cin("CIN-" + keycloakId)
                .email(keycloakId + "@example.com")
                .isEligible(eligible)
                .isDeleted(false)
                .build();
    }

    private static String commitmentKey(UUID citizenKeycloakId, UUID electionPublicId)
    {
        return citizenKeycloakId + "::" + electionPublicId;
    }
}
