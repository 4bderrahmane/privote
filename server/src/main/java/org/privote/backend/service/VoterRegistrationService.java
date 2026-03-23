package org.privote.backend.service;

import lombok.RequiredArgsConstructor;
import org.privote.backend.dto.election.VoterRegistrationRequestDto;
import org.privote.backend.dto.election.VoterRegistrationResponseDto;
import org.privote.backend.entity.Citizen;
import org.privote.backend.entity.CitizenElectionParticipation;
import org.privote.backend.entity.Election;
import org.privote.backend.entity.VoterCommitment;
import org.privote.backend.entity.enums.CommitmentStatus;
import org.privote.backend.entity.enums.ElectionPhase;
import org.privote.backend.entity.enums.ParticipationStatus;
import org.privote.backend.exception.ResourceNotFoundException;
import org.privote.backend.repository.CitizenElectionParticipationRepository;
import org.privote.backend.repository.CitizenRepository;
import org.privote.backend.repository.ElectionRepository;
import org.privote.backend.repository.VoterCommitmentRepository;
import org.privote.backend.web3.client.ElectionClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VoterRegistrationService
{
    private final ElectionRepository electionRepository;
    private final CitizenRepository citizenRepository;
    private final CitizenElectionParticipationRepository participationRepository;
    private final VoterCommitmentRepository voterCommitmentRepository;
    private final ElectionClient electionClient;
    private final TransactionOperations transactionOperations;

    public VoterRegistrationResponseDto registerMyCommitment(
            UUID electionPublicId,
            UUID citizenKeycloakId,
            VoterRegistrationRequestDto request
    )
    {
        if (request == null)
        {
            throw new IllegalArgumentException("Registration payload is required");
        }

        BigInteger commitmentValue = parseIdentityCommitment(request.getIdentityCommitment());
        String normalizedCommitment = commitmentValue.toString();
        PreparedRegistration prepared;
        try
        {
            prepared = preparePendingRegistration(electionPublicId, citizenKeycloakId, normalizedCommitment);
        }
        catch (DataIntegrityViolationException ex)
        {
            throw new IllegalStateException("identityCommitment is already registered for this election", ex);
        }

        if (prepared.alreadyOnChain())
        {
            return getMyRegistration(electionPublicId, citizenKeycloakId);
        }

        try
        {
            ChainEnrollment enrollment = enrollCommitmentOnChain(
                    prepared.contractAddress(),
                    prepared.groupId(),
                    commitmentValue
            );
            VoterRegistrationResponseDto response = finalizeSuccessfulRegistration(
                    prepared,
                    enrollment.transactionHash(),
                    enrollment.merkleLeafIndex()
            );
            if (response == null)
            {
                throw new IllegalStateException("Failed to persist voter commitment");
            }
            return response;
        }
        catch (Exception ex)
        {
            markCommitmentFailed(prepared.electionPublicId(), prepared.citizenKeycloakId());
            throw new IllegalStateException("Failed to enroll voter commitment on chain", ex);
        }
    }

    private PreparedRegistration preparePendingRegistration(
            UUID electionPublicId,
            UUID citizenKeycloakId,
            String normalizedCommitment
    )
    {
        PreparedRegistration prepared = transactionOperations.execute(status -> {
            Election election = resolveElection(electionPublicId);
            requireRegistrationOpen(election);
            String contractAddress = requireContractAddress(election);
            BigInteger groupId = requireGroupId(election);

            Citizen citizen = resolveCitizen(citizenKeycloakId);
            requireCitizenEligible(citizen);

            CitizenElectionParticipation participation = participationRepository
                    .findByCitizenKeycloakIdAndElectionPublicId(citizenKeycloakId, electionPublicId)
                    .orElseGet(() -> newParticipation(citizen, election));

            VoterCommitment commitment = voterCommitmentRepository
                    .findByCitizenKeycloakIdAndElectionPublicId(citizenKeycloakId, electionPublicId)
                    .orElseGet(() -> newCommitment(citizen, election));

            if (commitment.getIdentityCommitment() != null
                    && !commitment.getIdentityCommitment().equals(normalizedCommitment))
            {
                throw new IllegalStateException("Citizen already registered a different identityCommitment for this election");
            }

            voterCommitmentRepository.findByElectionPublicIdAndIdentityCommitment(electionPublicId, normalizedCommitment)
                    .ifPresent(existing -> {
                        UUID existingCitizen = existing.getCitizen().getKeycloakId();
                        if (existingCitizen != null && !existingCitizen.equals(citizenKeycloakId))
                        {
                            throw new IllegalStateException("identityCommitment is already registered for this election");
                        }
                    });

            participation.setStatus(ParticipationStatus.REGISTERED);
            participation.setCitizen(citizen);
            participation.setElection(election);
            participationRepository.save(participation);

            if (commitment.getStatus() == CommitmentStatus.ON_CHAIN
                    && normalizedCommitment.equals(commitment.getIdentityCommitment()))
            {
                return new PreparedRegistration(
                        electionPublicId,
                        citizenKeycloakId,
                        contractAddress,
                        groupId,
                        true
                );
            }

            commitment.setIdentityCommitment(normalizedCommitment);
            commitment.setStatus(CommitmentStatus.PENDING);
            commitment.setCitizen(citizen);
            commitment.setElection(election);
            voterCommitmentRepository.save(commitment);

            return new PreparedRegistration(
                    electionPublicId,
                    citizenKeycloakId,
                    contractAddress,
                    groupId,
                    false
            );
        });

        if (prepared == null)
        {
            throw new IllegalStateException("Failed to prepare voter registration");
        }
        return prepared;
    }

    private ChainEnrollment enrollCommitmentOnChain(
            String contractAddress,
            BigInteger groupId,
            BigInteger commitmentValue
    ) throws Exception
    {
        String transactionHash = null;
        if (!electionClient.hasMember(contractAddress, groupId, commitmentValue))
        {
            TransactionReceipt receipt = electionClient.addVoter(contractAddress, commitmentValue);
            transactionHash = normalizeHex(receipt == null ? null : receipt.getTransactionHash());
            if (transactionHash == null)
            {
                throw new IllegalStateException("Blockchain transaction hash is required when adding voter commitment");
            }
        }

        Long merkleLeafIndex = toLongOrNull(electionClient.indexOf(contractAddress, groupId, commitmentValue));
        return new ChainEnrollment(transactionHash, merkleLeafIndex);
    }

    private VoterRegistrationResponseDto finalizeSuccessfulRegistration(
            PreparedRegistration prepared,
            String transactionHash,
            Long merkleLeafIndex
    )
    {
        return transactionOperations.execute(status -> {
            Election election = resolveElection(prepared.electionPublicId());
            Citizen citizen = resolveCitizen(prepared.citizenKeycloakId());
            VoterCommitment commitment = voterCommitmentRepository
                    .findByCitizenKeycloakIdAndElectionPublicId(prepared.citizenKeycloakId(), prepared.electionPublicId())
                    .orElseGet(() -> newCommitment(citizen, election));

            commitment.setCitizen(citizen);
            commitment.setElection(election);
            commitment.setStatus(CommitmentStatus.ON_CHAIN);
            commitment.setMerkleLeafIndex(merkleLeafIndex);
            if (transactionHash != null)
            {
                commitment.setTransactionHash(transactionHash);
            }
            VoterCommitment savedCommitment = voterCommitmentRepository.save(commitment);

            CitizenElectionParticipation participation = participationRepository
                    .findByCitizenKeycloakIdAndElectionPublicId(prepared.citizenKeycloakId(), prepared.electionPublicId())
                    .orElseGet(() -> newParticipation(citizen, election));
            participation.setCitizen(citizen);
            participation.setElection(election);
            participation.setStatus(ParticipationStatus.REGISTERED);
            CitizenElectionParticipation savedParticipation = participationRepository.save(participation);

            return toResponse(election, citizen, savedParticipation, savedCommitment);
        });
    }

    private void markCommitmentFailed(UUID electionPublicId, UUID citizenKeycloakId)
    {
        transactionOperations.executeWithoutResult(status -> voterCommitmentRepository
                .findByCitizenKeycloakIdAndElectionPublicId(citizenKeycloakId, electionPublicId)
                .ifPresent(commitment -> {
                    commitment.setStatus(CommitmentStatus.FAILED);
                    voterCommitmentRepository.save(commitment);
                }));
    }

    public VoterRegistrationResponseDto getMyRegistration(UUID electionPublicId, UUID citizenKeycloakId)
    {
        Election election = resolveElection(electionPublicId);
        Citizen citizen = resolveCitizen(citizenKeycloakId);
        VoterCommitment commitment = voterCommitmentRepository
                .findByCitizenKeycloakIdAndElectionPublicId(citizenKeycloakId, electionPublicId)
                .orElseThrow(() -> new ResourceNotFoundException(VoterCommitment.class.getSimpleName(), "citizenKeycloakId", citizenKeycloakId));

        CitizenElectionParticipation participation = participationRepository
                .findByCitizenKeycloakIdAndElectionPublicId(citizenKeycloakId, electionPublicId)
                .orElseGet(() -> newParticipation(citizen, election));

        return toResponse(election, citizen, participation, commitment);
    }

    public List<VoterRegistrationResponseDto> getRegistrationsByElection(UUID electionPublicId)
    {
        Election election = resolveElection(electionPublicId);
        Map<UUID, CitizenElectionParticipation> participationsByCitizen = new HashMap<>();
        for (CitizenElectionParticipation participation : participationRepository.findByElectionPublicId(electionPublicId))
        {
            UUID citizenKeycloakId = participation.getCitizen() == null ? null : participation.getCitizen().getKeycloakId();
            if (citizenKeycloakId != null)
            {
                participationsByCitizen.put(citizenKeycloakId, participation);
            }
        }

        return voterCommitmentRepository.findByElectionPublicId(electionPublicId)
                .stream()
                .map(commitment ->
                {
                    Citizen citizen = commitment.getCitizen();
                    CitizenElectionParticipation participation = participationsByCitizen
                            .getOrDefault(citizen.getKeycloakId(), newParticipation(citizen, election));
                    return toResponse(election, citizen, participation, commitment);
                })
                .toList();
    }

    private Election resolveElection(UUID electionPublicId)
    {
        return electionRepository.findByPublicId(electionPublicId)
                .orElseThrow(() -> new ResourceNotFoundException(Election.class.getSimpleName(), "UUID", electionPublicId));
    }

    private Citizen resolveCitizen(UUID citizenKeycloakId)
    {
        return citizenRepository.findByKeycloakIdAndIsDeletedFalse(citizenKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException(Citizen.class.getSimpleName(), "keycloakId", citizenKeycloakId));
    }

    private static void requireCitizenEligible(Citizen citizen)
    {
        if (!citizen.isEligible())
        {
            throw new IllegalStateException("Citizen is not eligible to register for elections");
        }
    }

    private static void requireRegistrationOpen(Election election)
    {
        if (election.getPhase() != ElectionPhase.REGISTRATION)
        {
            throw new IllegalStateException("Election is not accepting voter registrations");
        }
        if (election.getEndTime() != null && !election.getEndTime().isAfter(Instant.now()))
        {
            throw new IllegalStateException("Election registration window has already elapsed");
        }
    }

    private static String requireContractAddress(Election election)
    {
        if (election.getContractAddress() == null || election.getContractAddress().isBlank())
        {
            throw new IllegalStateException("Election must be deployed before voter registration");
        }
        return election.getContractAddress();
    }

    private static BigInteger requireGroupId(Election election)
    {
        if (election.getExternalNullifier() == null || election.getExternalNullifier().signum() <= 0)
        {
            throw new IllegalStateException("Election externalNullifier is required for voter registration");
        }
        return election.getExternalNullifier();
    }

    private static BigInteger parseIdentityCommitment(String identityCommitment)
    {
        if (identityCommitment == null || identityCommitment.isBlank())
        {
            throw new IllegalArgumentException("identityCommitment is required");
        }

        try
        {
            BigInteger value = new BigInteger(identityCommitment.trim());
            if (value.signum() <= 0)
            {
                throw new IllegalArgumentException("identityCommitment must be a positive decimal integer");
            }
            return value;
        }
        catch (NumberFormatException ex)
        {
            throw new IllegalArgumentException("identityCommitment must be a positive decimal integer", ex);
        }
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

    private static CitizenElectionParticipation newParticipation(Citizen citizen, Election election)
    {
        CitizenElectionParticipation participation = new CitizenElectionParticipation();
        participation.setCitizen(citizen);
        participation.setElection(election);
        participation.setStatus(ParticipationStatus.REGISTERED);
        return participation;
    }

    private static VoterCommitment newCommitment(Citizen citizen, Election election)
    {
        VoterCommitment commitment = new VoterCommitment();
        commitment.setCitizen(citizen);
        commitment.setElection(election);
        commitment.setStatus(CommitmentStatus.PENDING);
        return commitment;
    }

    private static VoterRegistrationResponseDto toResponse(
            Election election,
            Citizen citizen,
            CitizenElectionParticipation participation,
            VoterCommitment commitment
    )
    {
        Instant registeredAt = commitment.getRegisteredAt() != null
                ? commitment.getRegisteredAt()
                : participation.getRegisteredAt();

        return new VoterRegistrationResponseDto(
                election.getPublicId(),
                citizen.getKeycloakId(),
                participation.getStatus(),
                commitment.getStatus(),
                commitment.getIdentityCommitment(),
                commitment.getMerkleLeafIndex(),
                commitment.getTransactionHash(),
                registeredAt
        );
    }

    private record PreparedRegistration(
            UUID electionPublicId,
            UUID citizenKeycloakId,
            String contractAddress,
            BigInteger groupId,
            boolean alreadyOnChain
    )
    {
    }

    private record ChainEnrollment(
            String transactionHash,
            Long merkleLeafIndex
    )
    {
    }
}
