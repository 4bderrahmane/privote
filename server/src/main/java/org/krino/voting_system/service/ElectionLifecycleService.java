package org.krino.voting_system.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.krino.voting_system.entity.Election;
import org.krino.voting_system.entity.enums.CandidateStatus;
import org.krino.voting_system.entity.enums.ElectionPhase;
import org.krino.voting_system.repository.CandidateRepository;
import org.krino.voting_system.repository.ElectionRepository;
import org.krino.voting_system.web3.client.ElectionClient;
import org.krino.voting_system.web3.client.ElectionFactoryClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ElectionLifecycleService
{
    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final ElectionFactoryClient electionFactoryClient;
    private final ElectionClient electionClient;

    public Election deployElection(UUID publicId)
    {
        Election election = requireElection(publicId);

        if (hasContractAddress(election))
        {
            throw new IllegalStateException("Election is already deployed");
        }

        if (election.getEndTime() == null)
        {
            throw new IllegalStateException("Election endTime must be set before deployment");
        }

        if (!election.getEndTime().isAfter(Instant.now()))
        {
            throw new IllegalStateException("Election endTime must be in the future before deployment");
        }

        ensureValidEncryptionKey(election);
        alignExternalNullifier(election);

        try
        {
            String existingOnChainAddress = electionFactoryClient.getElectionAddress(election.getPublicId());
            if (!isZeroAddress(existingOnChainAddress))
            {
                reconcileContractAddressAssignment(election, existingOnChainAddress);
                election.setContractAddress(existingOnChainAddress);
                return electionRepository.saveAndFlush(election);
            }

            String contractAddress = electionFactoryClient.createElection(
                    election.getPublicId(),
                    java.math.BigInteger.valueOf(election.getEndTime().getEpochSecond()),
                    election.getEncryptionPublicKey()
            );

            reconcileContractAddressAssignment(election, contractAddress);
            election.setContractAddress(contractAddress);
            return electionRepository.saveAndFlush(election);
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("Failed to deploy election contract: " + rootCauseMessage(ex), ex);
        }
    }

    public Election startElection(UUID publicId)
    {
        Election election = requireElection(publicId);
        requireDeployed(election);

        if (election.getPhase() != ElectionPhase.REGISTRATION)
        {
            throw new IllegalStateException("Only elections in REGISTRATION can be started");
        }

        if (election.getStartTime() != null && Instant.now().isBefore(election.getStartTime()))
        {
            throw new IllegalStateException("Election cannot be started before its configured startTime");
        }

        if (election.getEndTime() == null || !election.getEndTime().isAfter(Instant.now()))
        {
            throw new IllegalStateException("Election voting window has already elapsed");
        }

        requireActiveCandidates(election);

        try
        {
            electionClient.startElection(election.getContractAddress());
            election.setPhase(ElectionPhase.VOTING);
            election.setStartTime(Instant.now());
            return electionRepository.save(election);
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("Failed to start election on chain", ex);
        }
    }

    public Election endElection(UUID publicId, byte[] decryptionMaterial)
    {
        Election election = requireElection(publicId);
        requireDeployed(election);

        if (election.getPhase() != ElectionPhase.VOTING)
        {
            throw new IllegalStateException("Only elections in VOTING can be ended");
        }

        if (decryptionMaterial == null || decryptionMaterial.length == 0)
        {
            throw new IllegalStateException("Election decryptionMaterial is required before ending the election");
        }

        if (election.getEndTime() != null && Instant.now().isBefore(election.getEndTime()))
        {
            throw new IllegalStateException("Election cannot be ended before endTime");
        }

        try
        {
            electionClient.endElection(election.getContractAddress(), decryptionMaterial);
            election.setDecryptionMaterial(decryptionMaterial);
            election.setPhase(ElectionPhase.TALLY);
            return electionRepository.save(election);
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("Failed to end election on chain", ex);
        }
    }

    private Election requireElection(UUID publicId)
    {
        return electionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new org.krino.voting_system.exception.ResourceNotFoundException(Election.class.getSimpleName(), "UUID", publicId));
    }

    private static boolean hasContractAddress(Election election)
    {
        return election.getContractAddress() != null && !election.getContractAddress().isBlank();
    }

    private static void ensureValidEncryptionKey(Election election)
    {
        byte[] encryptionKey = election.getEncryptionPublicKey();
        if (encryptionKey == null || encryptionKey.length != 32)
        {
            throw new IllegalStateException("Election encryptionPublicKey must be exactly 32 bytes for deployment");
        }
    }

    private static void requireDeployed(Election election)
    {
        if (!hasContractAddress(election))
        {
            throw new IllegalStateException("Election must be deployed before this action");
        }
    }

    private static void alignExternalNullifier(Election election)
    {
        java.math.BigInteger canonical = ElectionService.deriveExternalNullifier(election.getPublicId());
        if (election.getExternalNullifier() != null && !canonical.equals(election.getExternalNullifier()))
        {
            throw new IllegalStateException("Election externalNullifier does not match the canonical UUID-derived value");
        }
        election.setExternalNullifier(canonical);
    }

    private void requireActiveCandidates(Election election)
    {
        if (!candidateRepository.existsByElectionPublicIdAndStatus(election.getPublicId(), CandidateStatus.ACTIVE))
        {
            throw new IllegalStateException("Election must have at least one ACTIVE candidate before voting can start");
        }
    }

    private void reconcileContractAddressAssignment(Election election, String contractAddress) throws Exception
    {
        Election conflictingElection = electionRepository.findByContractAddressIgnoreCase(contractAddress)
                .filter(existing -> !existing.getPublicId().equals(election.getPublicId()))
                .orElse(null);

        if (conflictingElection == null)
        {
            return;
        }

        if (canRecoverStaleLocalDeployment(conflictingElection, contractAddress))
        {
            clearStaleLocalDeployment(conflictingElection);
            electionRepository.saveAndFlush(conflictingElection);
            return;
        }

        throw new IllegalStateException(
                "Contract address " + contractAddress + " is already assigned to election " + conflictingElection.getPublicId()
        );
    }

    private boolean canRecoverStaleLocalDeployment(Election conflictingElection, String contractAddress) throws Exception
    {
        if (!electionFactoryClient.isLocalHardhatEnvironment())
        {
            return false;
        }

        String onChainAddress = electionFactoryClient.getElectionAddress(conflictingElection.getPublicId());
        return isZeroAddress(onChainAddress) || !contractAddress.equalsIgnoreCase(onChainAddress);
    }

    private void clearStaleLocalDeployment(Election election)
    {
        election.setContractAddress(null);
        election.setStartTime(null);
        election.setDecryptionMaterial(null);
        election.setPhase(ElectionPhase.REGISTRATION);
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

    private static boolean isZeroAddress(String address)
    {
        return address == null || "0x0000000000000000000000000000000000000000".equalsIgnoreCase(address);
    }
}
