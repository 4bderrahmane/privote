package org.privote.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.privote.backend.dto.ballot.BallotCastRequestDto;
import org.privote.backend.dto.ballot.BallotCastResponseDto;
import org.privote.backend.entity.*;
import org.privote.backend.entity.enums.CommitmentStatus;
import org.privote.backend.entity.enums.ElectionPhase;
import org.privote.backend.entity.enums.ParticipationStatus;
import org.privote.backend.exception.BusinessConflictException;
import org.privote.backend.exception.OperationFailedException;
import org.privote.backend.exception.VoteAlreadyCastException;
import org.privote.backend.repository.BallotRepository;
import org.privote.backend.repository.CitizenElectionParticipationRepository;
import org.privote.backend.repository.ElectionRepository;
import org.privote.backend.repository.VoterCommitmentRepository;
import org.privote.backend.web3.client.ElectionClient;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VoteServiceTest
{
    private final Map<UUID, Election> elections = new HashMap<>();
    private final Map<String, VoterCommitment> commitments = new HashMap<>();
    private final Map<String, CitizenElectionParticipation> participations = new HashMap<>();
    private final Map<UUID, Ballot> ballots = new HashMap<>();

    private static Election votingElection(UUID publicId)
    {
        Election election = new Election();
        election.setPublicId(publicId);
        election.setPhase(ElectionPhase.VOTING);
        election.setTitle("Vote test");
        election.setCoordinator(Citizen.builder().keycloakId(UUID.randomUUID()).build());
        election.setEndTime(Instant.now().plusSeconds(3600));
        election.setContractAddress("0xabc0000000000000000000000000000000000000");
        election.setEncryptionPublicKey(new byte[32]);
        return election;
    }

    private static BallotCastRequestDto request(String nullifier)
    {
        BallotCastRequestDto request = new BallotCastRequestDto();
        request.setCiphertext(new byte[]{1, 2, 3, 4});
        request.setNullifier(nullifier);
        request.setProof(java.util.List.of("1", "2", "3", "4", "5", "6", "7", "8"));
        return request;
    }

    private static String commitmentKey(UUID citizenKeycloakId, UUID electionPublicId)
    {
        return citizenKeycloakId + "::" + electionPublicId;
    }

    private static TransactionOperations transactionOperationsNoOp()
    {
        return new TransactionOperations()
        {
            @Override
            public <T> T execute(TransactionCallback<T> action)
            {
                return action.doInTransaction(new SimpleTransactionStatus());
            }
        };
    }

    @BeforeEach
    void setUp()
    {
        elections.clear();
        commitments.clear();
        participations.clear();
        ballots.clear();
    }

    @Test
    void castMyVotePersistsBallotAndMarksParticipationCast()
    {
        UUID electionPublicId = UUID.randomUUID();
        UUID citizenKeycloakId = UUID.randomUUID();
        Election election = votingElection(electionPublicId);
        elections.put(electionPublicId, election);

        Citizen citizen = Citizen.builder().keycloakId(citizenKeycloakId).build();
        VoterCommitment commitment = new VoterCommitment();
        commitment.setCitizen(citizen);
        commitment.setElection(election);
        commitment.setIdentityCommitment("111");
        commitment.setStatus(CommitmentStatus.ON_CHAIN);
        commitments.put(commitmentKey(citizenKeycloakId, electionPublicId), commitment);

        CitizenElectionParticipation participation = new CitizenElectionParticipation();
        participation.setCitizen(citizen);
        participation.setElection(election);
        participation.setStatus(ParticipationStatus.REGISTERED);
        participations.put(commitmentKey(citizenKeycloakId, electionPublicId), participation);

        VoteService voteService = new VoteService(
                electionRepositoryStub(),
                commitmentRepositoryStub(),
                participationRepositoryStub(),
                ballotRepositoryStub(),
                new ElectionClient(null, null, null)
                {
                    @Override
                    public boolean isNullifierUsed(String electionAddress, BigInteger nullifierHash)
                    {
                        assertEquals("0xabc0000000000000000000000000000000000000", electionAddress);
                        assertEquals(new BigInteger("222"), nullifierHash);
                        return false;
                    }

                    @Override
                    public TransactionReceipt castVote(String electionAddress, byte[] ciphertext, BigInteger nullifier, java.util.List<BigInteger> proof)
                    {
                        assertEquals("0xabc0000000000000000000000000000000000000", electionAddress);
                        assertEquals(new BigInteger("222"), nullifier);
                        assertEquals(8, proof.size());

                        TransactionReceipt receipt = new TransactionReceipt();
                        receipt.setTransactionHash("0xfeed");
                        receipt.setBlockNumber("42");
                        return receipt;
                    }
                },
                transactionOperationsNoOp()
        );

        BallotCastResponseDto response = voteService.castMyVote(
                electionPublicId,
                citizenKeycloakId,
                request("222")
        );

        assertNotNull(response.ballotId());
        assertEquals(electionPublicId, response.electionPublicId());
        assertEquals("222", response.nullifier());
        assertEquals("0xfeed", response.transactionHash());
        assertEquals(42L, response.blockNumber());
        assertEquals(ParticipationStatus.CAST, participation.getStatus());
        assertEquals(1, ballots.size());
    }

    @Test
    void castMyVoteRequiresOnChainCommitment()
    {
        UUID electionPublicId = UUID.randomUUID();
        UUID citizenKeycloakId = UUID.randomUUID();
        Election election = votingElection(electionPublicId);
        elections.put(electionPublicId, election);

        Citizen citizen = Citizen.builder().keycloakId(citizenKeycloakId).build();
        VoterCommitment commitment = new VoterCommitment();
        commitment.setCitizen(citizen);
        commitment.setElection(election);
        commitment.setIdentityCommitment("111");
        commitment.setStatus(CommitmentStatus.PENDING);
        commitments.put(commitmentKey(citizenKeycloakId, electionPublicId), commitment);

        VoteService voteService = new VoteService(
                electionRepositoryStub(),
                commitmentRepositoryStub(),
                participationRepositoryStub(),
                ballotRepositoryStub(),
                new ElectionClient(null, null, null),
                transactionOperationsNoOp()
        );

        BusinessConflictException ex = assertThrows(
                BusinessConflictException.class,
                () -> voteService.castMyVote(electionPublicId, citizenKeycloakId, request("222"))
        );

        assertEquals("Citizen must complete voter registration before voting", ex.getMessage());
    }

    @Test
    void castMyVoteRejectsDuplicateNullifier()
    {
        UUID electionPublicId = UUID.randomUUID();
        UUID citizenKeycloakId = UUID.randomUUID();
        Election election = votingElection(electionPublicId);
        elections.put(electionPublicId, election);

        Citizen citizen = Citizen.builder().keycloakId(citizenKeycloakId).build();
        VoterCommitment commitment = new VoterCommitment();
        commitment.setCitizen(citizen);
        commitment.setElection(election);
        commitment.setIdentityCommitment("111");
        commitment.setStatus(CommitmentStatus.ON_CHAIN);
        commitments.put(commitmentKey(citizenKeycloakId, electionPublicId), commitment);

        Ballot existing = new Ballot();
        existing.setId(UUID.randomUUID());
        existing.setElection(election);
        existing.setNullifier("222");
        existing.setTransactionHash("0xdead");
        ballots.put(existing.getId(), existing);

        VoteService voteService = new VoteService(
                electionRepositoryStub(),
                commitmentRepositoryStub(),
                participationRepositoryStub(),
                ballotRepositoryStub(),
                new ElectionClient(null, null, null),
                transactionOperationsNoOp()
        );

        VoteAlreadyCastException ex = assertThrows(
                VoteAlreadyCastException.class,
                () -> voteService.castMyVote(electionPublicId, citizenKeycloakId, request("222"))
        );

        assertEquals("A vote has already been cast for this election identity", ex.getMessage());
    }

    @Test
    void castMyVoteFailsWhenChainReceiptHasNoTransactionHash()
    {
        UUID electionPublicId = UUID.randomUUID();
        UUID citizenKeycloakId = UUID.randomUUID();
        Election election = votingElection(electionPublicId);
        elections.put(electionPublicId, election);

        Citizen citizen = Citizen.builder().keycloakId(citizenKeycloakId).build();
        VoterCommitment commitment = new VoterCommitment();
        commitment.setCitizen(citizen);
        commitment.setElection(election);
        commitment.setIdentityCommitment("111");
        commitment.setStatus(CommitmentStatus.ON_CHAIN);
        commitments.put(commitmentKey(citizenKeycloakId, electionPublicId), commitment);

        VoteService voteService = new VoteService(
                electionRepositoryStub(),
                commitmentRepositoryStub(),
                participationRepositoryStub(),
                ballotRepositoryStub(),
                new ElectionClient(null, null, null)
                {
                    @Override
                    public boolean isNullifierUsed(String electionAddress, BigInteger nullifierHash)
                    {
                        return false;
                    }

                    @Override
                    public TransactionReceipt castVote(String electionAddress, byte[] ciphertext, BigInteger nullifier, java.util.List<BigInteger> proof)
                    {
                        return new TransactionReceipt();
                    }
                },
                transactionOperationsNoOp()
        );

        OperationFailedException ex = assertThrows(
                OperationFailedException.class,
                () -> voteService.castMyVote(electionPublicId, citizenKeycloakId, request("222"))
        );

        assertEquals("Failed to cast vote on chain: Blockchain transaction hash is required in the vote receipt", ex.getMessage());
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
                    default ->
                            throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
                }
        );
    }

    private VoterCommitmentRepository commitmentRepositoryStub()
    {
        return (VoterCommitmentRepository) Proxy.newProxyInstance(
                VoterCommitmentRepository.class.getClassLoader(),
                new Class[]{VoterCommitmentRepository.class},
                (proxy, method, args) -> switch (method.getName())
                {
                    case "findByCitizenKeycloakIdAndElectionPublicId" ->
                            Optional.ofNullable(commitments.get(commitmentKey((UUID) args[0], (UUID) args[1])));
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "VoterCommitmentRepositoryStub";
                    default ->
                            throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
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
                        participations.put(
                                commitmentKey(participation.getCitizen().getKeycloakId(), participation.getElection().getPublicId()),
                                participation
                        );
                        yield participation;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "CitizenElectionParticipationRepositoryStub";
                    default ->
                            throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
                }
        );
    }

    private BallotRepository ballotRepositoryStub()
    {
        return (BallotRepository) Proxy.newProxyInstance(
                BallotRepository.class.getClassLoader(),
                new Class[]{BallotRepository.class},
                (proxy, method, args) -> switch (method.getName())
                {
                    case "findByElectionPublicIdAndNullifier" -> ballots.values().stream()
                            .filter(ballot -> ballot.getElection().getPublicId().equals(args[0]))
                            .filter(ballot -> ballot.getNullifier().equals(args[1]))
                            .findFirst();
                    case "saveAndFlush" ->
                    {
                        Ballot ballot = (Ballot) args[0];
                        if (ballot.getId() == null)
                        {
                            ballot.setId(UUID.randomUUID());
                        }
                        if (ballot.getCastAt() == null)
                        {
                            ballot.setCastAt(Instant.now());
                        }
                        ballots.put(ballot.getId(), ballot);
                        yield ballot;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "BallotRepositoryStub";
                    default ->
                            throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
                }
        );
    }
}
