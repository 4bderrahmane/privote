package org.krino.voting_system.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.krino.voting_system.dto.election.VoterRegistrationRequestDto;
import org.krino.voting_system.dto.election.VoterRegistrationResponseDto;
import org.krino.voting_system.entity.Citizen;
import org.krino.voting_system.entity.CitizenElectionParticipation;
import org.krino.voting_system.entity.Election;
import org.krino.voting_system.entity.VoterCommitment;
import org.krino.voting_system.entity.enums.CommitmentStatus;
import org.krino.voting_system.entity.enums.ElectionPhase;
import org.krino.voting_system.entity.enums.ParticipationStatus;
import org.krino.voting_system.exception.ResourceNotFoundException;
import org.krino.voting_system.repository.CitizenElectionParticipationRepository;
import org.krino.voting_system.repository.CitizenRepository;
import org.krino.voting_system.repository.ElectionRepository;
import org.krino.voting_system.repository.VoterCommitmentRepository;
import org.krino.voting_system.web3.client.ElectionClient;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class VoterRegistrationService
{
    private final ElectionRepository electionRepository;
    private final CitizenRepository citizenRepository;
    private final CitizenElectionParticipationRepository participationRepository;
    private final VoterCommitmentRepository voterCommitmentRepository;
    private final ElectionClient electionClient;

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

        Election election = resolveElection(electionPublicId);
        requireRegistrationOpen(election);

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
                .ifPresent(existing ->
                {
                    UUID existingCitizen = existing.getCitizen().getKeycloakId();
                    if (existingCitizen != null && !existingCitizen.equals(citizenKeycloakId))
                    {
                        throw new IllegalStateException("identityCommitment is already registered for this election");
                    }
                });

        commitment.setIdentityCommitment(normalizedCommitment);
        commitment.setStatus(CommitmentStatus.PENDING);
        commitment.setCitizen(citizen);
        commitment.setElection(election);
        participation.setStatus(ParticipationStatus.REGISTERED);
        participation.setCitizen(citizen);
        participation.setElection(election);

        BigInteger groupId = requireGroupId(election);
        String contractAddress = requireContractAddress(election);

        participationRepository.save(participation);
        voterCommitmentRepository.save(commitment);

        try
        {
            if (!electionClient.hasMember(contractAddress, groupId, commitmentValue))
            {
                TransactionReceipt receipt = electionClient.addVoter(contractAddress, commitmentValue);
                commitment.setTransactionHash(receipt == null ? null : receipt.getTransactionHash());
            }

            commitment.setMerkleLeafIndex(toLongOrNull(electionClient.indexOf(contractAddress, groupId, commitmentValue)));
            commitment.setStatus(CommitmentStatus.ON_CHAIN);

            participationRepository.save(participation);
            voterCommitmentRepository.save(commitment);

            return toResponse(election, citizen, participation, commitment);
        }
        catch (Exception ex)
        {
            commitment.setStatus(CommitmentStatus.FAILED);
            voterCommitmentRepository.save(commitment);
            throw new IllegalStateException("Failed to enroll voter commitment on chain", ex);
        }
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
}
