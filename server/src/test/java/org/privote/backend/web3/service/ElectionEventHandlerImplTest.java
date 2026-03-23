package org.privote.backend.web3.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.privote.backend.entity.Citizen;
import org.privote.backend.entity.CitizenElectionParticipation;
import org.privote.backend.entity.Election;
import org.privote.backend.entity.VoterCommitment;
import org.privote.backend.entity.enums.CommitmentStatus;
import org.privote.backend.entity.enums.ElectionPhase;
import org.privote.backend.entity.enums.ParticipationStatus;
import org.privote.backend.repository.CitizenElectionParticipationRepository;
import org.privote.backend.repository.ElectionRepository;
import org.privote.backend.repository.VoterCommitmentRepository;
import org.privote.backend.web3.listener.events.ElectionEndedEvent;
import org.privote.backend.web3.listener.events.ElectionStartedEvent;
import org.privote.backend.web3.listener.events.MemberAddedEvent;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ElectionEventHandlerImplTest
{
    private final Map<String, Election> electionsByContract = new HashMap<>();
    private final Map<String, VoterCommitment> commitments = new HashMap<>();
    private final Map<String, CitizenElectionParticipation> participations = new HashMap<>();
    private ElectionEventHandlerImpl handler;

    @BeforeEach
    void setUp()
    {
        electionsByContract.clear();
        commitments.clear();
        participations.clear();
        handler = new ElectionEventHandlerImpl(
                repositoryStub(),
                voterCommitmentRepositoryStub(),
                participationRepositoryStub()
        );
    }

    @Test
    void onElectionStartedMarksElectionVotingAndAppliesChainTimes()
    {
        Election election = election("0xabc0000000000000000000000000000000000000", ElectionPhase.REGISTRATION);
        electionsByContract.put(election.getContractAddress(), election);

        handler.onElectionStarted(new ElectionStartedEvent(
                "ABC0000000000000000000000000000000000000",
                "0xcoordinator",
                BigInteger.valueOf(1_710_000_000L),
                BigInteger.valueOf(1_710_003_600L),
                "0xtx",
                BigInteger.TEN,
                BigInteger.ZERO
        ));

        assertEquals(ElectionPhase.VOTING, election.getPhase());
        assertEquals(Instant.ofEpochSecond(1_710_000_000L), election.getStartTime());
        assertEquals(Instant.ofEpochSecond(1_710_003_600L), election.getEndTime());
    }

    @Test
    void onMemberAddedMarksCommitmentOnChainAndCreatesParticipationIfMissing()
    {
        Election election = election("0xabc0000000000000000000000000000000000000", ElectionPhase.REGISTRATION);
        election.setExternalNullifier(BigInteger.valueOf(42));
        electionsByContract.put(election.getContractAddress(), election);

        Citizen citizen = Citizen.builder().keycloakId(UUID.randomUUID()).build();
        VoterCommitment commitment = new VoterCommitment();
        commitment.setCitizen(citizen);
        commitment.setElection(election);
        commitment.setIdentityCommitment("123456");
        commitment.setStatus(CommitmentStatus.PENDING);
        commitments.put(commitmentKey(citizen.getKeycloakId(), election.getPublicId()), commitment);

        handler.onMemberAdded(new MemberAddedEvent(
                "0xAbC0000000000000000000000000000000000000",
                BigInteger.valueOf(42),
                BigInteger.valueOf(9),
                new BigInteger("123456"),
                BigInteger.TEN,
                "0xtxmember",
                BigInteger.valueOf(11),
                BigInteger.ZERO
        ));

        assertEquals(CommitmentStatus.ON_CHAIN, commitment.getStatus());
        assertEquals(9L, commitment.getMerkleLeafIndex());
        assertEquals("0xtxmember", commitment.getTransactionHash());

        CitizenElectionParticipation participation = participations.get(commitmentKey(citizen.getKeycloakId(), election.getPublicId()));
        assertEquals(ParticipationStatus.REGISTERED, participation.getStatus());
    }

    @Test
    void onElectionEndedMarksElectionTallyAndStoresDecryptionMaterial()
    {
        Election election = election("0xabc0000000000000000000000000000000000000", ElectionPhase.VOTING);
        electionsByContract.put(election.getContractAddress(), election);

        byte[] decryptionMaterial = new byte[]{1, 2, 3, 4};
        handler.onElectionEnded(new ElectionEndedEvent(
                "0xAbC0000000000000000000000000000000000000",
                "0xcoordinator",
                decryptionMaterial,
                "0xtx",
                BigInteger.TEN,
                BigInteger.ONE
        ));

        assertEquals(ElectionPhase.TALLY, election.getPhase());
        assertArrayEquals(decryptionMaterial, election.getDecryptionMaterial());
    }

    private ElectionRepository repositoryStub()
    {
        return (ElectionRepository) Proxy.newProxyInstance(
                ElectionRepository.class.getClassLoader(),
                new Class[]{ElectionRepository.class},
                (proxy, method, args) -> switch (method.getName())
                {
                    case "findByContractAddressIgnoreCase" ->
                    {
                        String contractAddress = ((String) args[0]).toLowerCase();
                        yield Optional.ofNullable(electionsByContract.get(contractAddress));
                    }
                    case "findByPublicId" -> Optional.empty();
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "ElectionRepositoryStub";
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
                    case "findByElectionPublicIdAndIdentityCommitment" ->
                            commitments.values().stream()
                                    .filter(commitment -> commitment.getElection().getPublicId().equals(args[0]))
                                    .filter(commitment -> commitment.getIdentityCommitment().equals(args[1]))
                                    .findFirst();
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

    private CitizenElectionParticipationRepository participationRepositoryStub()
    {
        return (CitizenElectionParticipationRepository) Proxy.newProxyInstance(
                CitizenElectionParticipationRepository.class.getClassLoader(),
                new Class[]{CitizenElectionParticipationRepository.class},
                (proxy, method, args) -> switch (method.getName())
                {
                    case "findByCitizenKeycloakIdAndElectionPublicId" ->
                            Optional.ofNullable(participations.get(commitmentKey((UUID) args[0], (UUID) args[1])));
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

    private static Election election(String contractAddress, ElectionPhase phase)
    {
        Election election = new Election();
        election.setPublicId(UUID.randomUUID());
        election.setContractAddress(contractAddress);
        election.setPhase(phase);
        election.setStartTime(null);
        election.setEndTime(Instant.now().plusSeconds(3_600));
        election.setCoordinator(Citizen.builder().keycloakId(UUID.randomUUID()).build());
        election.setTitle("Test election");
        election.setExternalNullifier(BigInteger.ONE);
        election.setEncryptionPublicKey(new byte[32]);
        return election;
    }

    private static String commitmentKey(UUID citizenKeycloakId, UUID electionPublicId)
    {
        return citizenKeycloakId + "::" + electionPublicId;
    }
}
