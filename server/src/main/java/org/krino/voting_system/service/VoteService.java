package org.krino.voting_system.service;

import lombok.RequiredArgsConstructor;
import org.krino.voting_system.dto.ballot.BallotCastRequestDto;
import org.krino.voting_system.dto.ballot.BallotCastResponseDto;
import org.krino.voting_system.entity.Ballot;
import org.krino.voting_system.entity.CitizenElectionParticipation;
import org.krino.voting_system.entity.Election;
import org.krino.voting_system.entity.VoterCommitment;
import org.krino.voting_system.entity.enums.CommitmentStatus;
import org.krino.voting_system.entity.enums.ElectionPhase;
import org.krino.voting_system.entity.enums.ParticipationStatus;
import org.krino.voting_system.exception.ResourceNotFoundException;
import org.krino.voting_system.exception.VoteAlreadyCastException;
import org.krino.voting_system.repository.BallotRepository;
import org.krino.voting_system.repository.CitizenElectionParticipationRepository;
import org.krino.voting_system.repository.ElectionRepository;
import org.krino.voting_system.repository.VoterCommitmentRepository;
import org.krino.voting_system.web3.client.ElectionClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.web3j.crypto.Hash;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VoteService
{
    private final ElectionRepository electionRepository;
    private final VoterCommitmentRepository voterCommitmentRepository;
    private final CitizenElectionParticipationRepository participationRepository;
    private final BallotRepository ballotRepository;
    private final ElectionClient electionClient;
    private final TransactionOperations transactionOperations;

    public BallotCastResponseDto castMyVote(
            UUID electionPublicId,
            UUID citizenKeycloakId,
            BallotCastRequestDto request
    )
    {
        validateRequest(request);

        Election election = resolveElection(electionPublicId);
        requireVotingOpen(election);
        String contractAddress = requireContractAddress(election);

        VoterCommitment commitment = voterCommitmentRepository
                .findByCitizenKeycloakIdAndElectionPublicId(citizenKeycloakId, electionPublicId)
                .orElseThrow(() -> new IllegalStateException("Citizen has not registered a voting identity for this election"));
        requireOnChainCommitment(commitment);

        BigInteger nullifier = parsePositiveInteger(request.getNullifier(), "nullifier");
        List<BigInteger> proof = parseProof(request.getProof());
        String normalizedNullifier = nullifier.toString();

        if (ballotRepository.findByElectionPublicIdAndNullifier(electionPublicId, normalizedNullifier).isPresent())
        {
            throw new VoteAlreadyCastException("A vote has already been cast for this election identity");
        }

        try
        {
            if (electionClient.isNullifierUsed(contractAddress, nullifier))
            {
                throw new VoteAlreadyCastException("A vote has already been cast for this election identity");
            }

            TransactionReceipt receipt = electionClient.castVote(
                    contractAddress,
                    request.getCiphertext(),
                    nullifier,
                    proof
            );
            String transactionHash = requireTransactionHash(receipt);
            Long blockNumber = toLongOrNull(receipt == null ? null : receipt.getBlockNumber());

            BallotCastResponseDto response = persistCastVote(
                    electionPublicId,
                    citizenKeycloakId,
                    request.getCiphertext(),
                    proof,
                    normalizedNullifier,
                    transactionHash,
                    blockNumber
            );
            if (response == null)
            {
                throw new IllegalStateException("Failed to persist cast vote");
            }
            return response;
        }
        catch (VoteAlreadyCastException ex)
        {
            throw ex;
        }
        catch (DataIntegrityViolationException ex)
        {
            throw new VoteAlreadyCastException("A vote has already been cast for this election identity");
        }
        catch (Exception ex)
        {
            if (isDataConflict(ex))
            {
                throw new VoteAlreadyCastException("A vote has already been cast for this election identity");
            }
            if (ballotRepository.findByElectionPublicIdAndNullifier(electionPublicId, normalizedNullifier).isPresent())
            {
                throw new VoteAlreadyCastException("A vote has already been cast for this election identity");
            }
            throw new IllegalStateException("Failed to cast vote on chain: " + rootCauseMessage(ex), ex);
        }
    }

    private BallotCastResponseDto persistCastVote(
            UUID electionPublicId,
            UUID citizenKeycloakId,
            byte[] ciphertext,
            List<BigInteger> proof,
            String normalizedNullifier,
            String transactionHash,
            Long blockNumber
    )
    {
        return transactionOperations.execute(status -> {
            Election election = resolveElection(electionPublicId);
            VoterCommitment commitment = voterCommitmentRepository
                    .findByCitizenKeycloakIdAndElectionPublicId(citizenKeycloakId, electionPublicId)
                    .orElseThrow(() -> new IllegalStateException("Citizen has not registered a voting identity for this election"));
            requireOnChainCommitment(commitment);

            CitizenElectionParticipation participation = participationRepository
                    .findByCitizenKeycloakIdAndElectionPublicId(citizenKeycloakId, electionPublicId)
                    .orElseGet(() -> {
                        CitizenElectionParticipation created = new CitizenElectionParticipation();
                        created.setCitizen(commitment.getCitizen());
                        created.setElection(election);
                        created.setStatus(ParticipationStatus.REGISTERED);
                        return created;
                    });

            if (ballotRepository.findByElectionPublicIdAndNullifier(electionPublicId, normalizedNullifier).isPresent())
            {
                throw new VoteAlreadyCastException("A vote has already been cast for this election identity");
            }

            Ballot ballot = new Ballot();
            ballot.setElection(election);
            ballot.setCiphertext(ciphertext.clone());
            ballot.setCiphertextHash(normalizeHex(Numeric.toHexString(Hash.sha3(ciphertext))));
            ballot.setNullifier(normalizedNullifier);
            ballot.setZkProof(serializeProof(proof));
            ballot.setTransactionHash(transactionHash);
            ballot.setBlockNumber(blockNumber);

            Ballot storedBallot = ballotRepository.saveAndFlush(ballot);
            participation.setStatus(ParticipationStatus.CAST);
            participationRepository.save(participation);
            return BallotCastResponseDto.fromEntity(storedBallot);
        });
    }

    private Election resolveElection(UUID electionPublicId)
    {
        return electionRepository.findByPublicId(electionPublicId)
                .orElseThrow(() -> new ResourceNotFoundException(Election.class.getSimpleName(), "UUID", electionPublicId));
    }

    private static void validateRequest(BallotCastRequestDto request)
    {
        if (request == null)
        {
            throw new IllegalArgumentException("Vote payload is required");
        }
        if (request.getCiphertext() == null || request.getCiphertext().length == 0)
        {
            throw new IllegalArgumentException("ciphertext is required");
        }
        if (request.getNullifier() == null || request.getNullifier().isBlank())
        {
            throw new IllegalArgumentException("nullifier is required");
        }
        if (request.getProof() == null || request.getProof().isEmpty())
        {
            throw new IllegalArgumentException("proof is required");
        }
    }

    private static void requireVotingOpen(Election election)
    {
        if (election.getPhase() != ElectionPhase.VOTING)
        {
            throw new IllegalStateException("Election is not currently accepting votes");
        }
        if (election.getEndTime() != null && !election.getEndTime().isAfter(Instant.now()))
        {
            throw new IllegalStateException("Election voting window has already elapsed");
        }
    }

    private static String requireContractAddress(Election election)
    {
        if (election.getContractAddress() == null || election.getContractAddress().isBlank())
        {
            throw new IllegalStateException("Election must be deployed before votes can be cast");
        }
        return election.getContractAddress();
    }

    private static void requireOnChainCommitment(VoterCommitment commitment)
    {
        if (commitment.getStatus() != CommitmentStatus.ON_CHAIN)
        {
            throw new IllegalStateException("Citizen must complete voter registration before voting");
        }
        if (commitment.getIdentityCommitment() == null || commitment.getIdentityCommitment().isBlank())
        {
            throw new IllegalStateException("Citizen must complete voter registration before voting");
        }
    }

    private static BigInteger parsePositiveInteger(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required");
        }

        try
        {
            BigInteger parsed = new BigInteger(value.trim());
            if (parsed.signum() <= 0)
            {
                throw new IllegalArgumentException(label + " must be a positive decimal integer");
            }
            return parsed;
        }
        catch (NumberFormatException ex)
        {
            throw new IllegalArgumentException(label + " must be a positive decimal integer", ex);
        }
    }

    private static List<BigInteger> parseProof(List<String> proof)
    {
        if (proof == null || proof.size() != 8)
        {
            throw new IllegalArgumentException("proof must contain exactly 8 field elements");
        }

        List<BigInteger> parsed = new ArrayList<>(proof.size());
        for (int index = 0; index < proof.size(); index += 1)
        {
            parsed.add(parsePositiveInteger(proof.get(index), "proof[" + index + "]"));
        }
        return parsed;
    }

    private static String serializeProof(List<BigInteger> proof)
    {
        return proof.stream()
                .map(BigInteger::toString)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static Long toLongOrNull(BigInteger value)
    {
        if (value == null)
        {
            return null;
        }

        try
        {
            return value.longValueExact();
        }
        catch (ArithmeticException ex)
        {
            return null;
        }
    }

    private static String requireTransactionHash(TransactionReceipt receipt)
    {
        String transactionHash = normalizeHex(receipt == null ? null : receipt.getTransactionHash());
        if (transactionHash == null)
        {
            throw new IllegalStateException("Blockchain transaction hash is required in the vote receipt");
        }
        return transactionHash;
    }

    private static boolean isDataConflict(Throwable throwable)
    {
        Throwable current = throwable;
        while (current != null)
        {
            if (current instanceof DataIntegrityViolationException)
            {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String normalizeHex(String value)
    {
        if (value == null)
        {
            return null;
        }

        String normalized = value.trim();
        if (normalized.isEmpty())
        {
            return null;
        }
        if (!normalized.startsWith("0x"))
        {
            normalized = "0x" + normalized;
        }
        return normalized.toLowerCase();
    }

    private static String rootCauseMessage(Exception ex)
    {
        Throwable current = ex;
        while (current.getCause() != null)
        {
            current = current.getCause();
        }

        String message = current.getMessage();
        if (message == null || message.isBlank())
        {
            return current.getClass().getSimpleName();
        }
        return message;
    }
}
