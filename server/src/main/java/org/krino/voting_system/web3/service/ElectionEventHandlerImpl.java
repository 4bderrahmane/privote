package org.krino.voting_system.web3.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.krino.voting_system.entity.Election;
import org.krino.voting_system.entity.VoterCommitment;
import org.krino.voting_system.entity.enums.CommitmentStatus;
import org.krino.voting_system.entity.enums.ElectionPhase;
import org.krino.voting_system.entity.enums.ParticipationStatus;
import org.krino.voting_system.repository.CitizenElectionParticipationRepository;
import org.krino.voting_system.repository.ElectionRepository;
import org.krino.voting_system.web3.listener.events.ElectionDeployedEvent;
import org.krino.voting_system.web3.listener.events.ElectionEndedEvent;
import org.krino.voting_system.web3.listener.ElectionEventHandler;
import org.krino.voting_system.web3.listener.events.ElectionStartedEvent;
import org.krino.voting_system.web3.listener.events.MemberAddedEvent;
import org.krino.voting_system.repository.VoterCommitmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElectionEventHandlerImpl implements ElectionEventHandler
{

    private final ElectionRepository electionRepository;
    private final VoterCommitmentRepository voterCommitmentRepository;
    private final CitizenElectionParticipationRepository participationRepository;

    @Override
    @Transactional
    public void onElectionDeployed(ElectionDeployedEvent event)
    {
        final String contract = normalizeAddress(event.electionAddress());
        if (contract == null)
        {
            log.warn("ElectionDeployed ignored: invalid contract address. uuid={}, addr={}, tx={}",
                    event.uuid(), event.electionAddress(), event.txHash());
            return;
        }

        // 1) Find election by uuid (publicId)
        Election election = electionRepository.findByPublicId(event.uuid()).orElse(null);
        if (election == null)
        {
            log.warn("ElectionDeployed for unknown uuid. uuid={}, contract={}, tx={}",
                    event.uuid(), contract, event.txHash());
            return;
        }

        // 2) Idempotency: already linked?
        String existing = normalizeAddress(election.getContractAddress());
        if (existing != null)
        {
            if (existing.equals(contract))
            {
                return; // already processed
            }
            log.error("Election {} already linked to contract={}, but event says contract={}. tx={}",
                    election.getPublicId(), existing, contract, event.txHash());
            return;
        }

        // 3) Safety: contract not already used by another election
        Optional<Election> other = electionRepository.findByContractAddressIgnoreCase(contract);
        if (other.isPresent())
        {
            log.error("Contract {} already linked to election uuid={}, cannot link to uuid={}. tx={}",
                    contract, other.get().getPublicId(), event.uuid(), event.txHash());
            return;
        }

        // 4) External nullifier consistency
        if (event.externalNullifier() == null || event.externalNullifier().signum() <= 0)
        {
            log.error("ElectionDeployed has invalid externalNullifier. uuid={}, tx={}", event.uuid(), event.txHash());
            return;
        }

        if (election.getExternalNullifier() != null
                && election.getExternalNullifier().signum() > 0
                && !election.getExternalNullifier().equals(event.externalNullifier()))
        {
            log.error("externalNullifier mismatch for uuid={}. db={}, chain={}, tx={}",
                    election.getPublicId(), election.getExternalNullifier(), event.externalNullifier(), event.txHash());
            return;
        }

        // 5) End time consistency (chain is seconds)
        Instant chainEnd = toInstantSeconds(event.endTimeSeconds());
        if (chainEnd != null && election.getEndTime() != null)
        {
            long delta = Math.abs(election.getEndTime().getEpochSecond() - chainEnd.getEpochSecond());
            if (delta > 60)
            {
                log.warn("endTime mismatch for uuid={}. dbEnd={}, chainEnd={}, deltaSeconds={}, tx={}",
                        election.getPublicId(), election.getEndTime(), chainEnd, delta, event.txHash());
            }
        }

        // 6) Apply updates
        election.setContractAddress(contract);

        // If you want chain to be authoritative for endTime:
        if (chainEnd != null)
        {
            election.setEndTime(chainEnd);
        }

        // Ensure externalNullifier is stored (if you didn't fill it pre-deploy)
        election.setExternalNullifier(event.externalNullifier());

        // You said phase mirrors Semaphore. After deploy you're still in REGISTRATION (typically).
        // election.setPhase(ElectionPhase.REGISTRATION);

        log.info("Linked election uuid={} to contract={} at block={} logIndex={} tx={}",
                election.getPublicId(), contract, event.blockNumber(), event.logIndex(), event.txHash());
    }

    @Override
    @Transactional
    public void onMemberAdded(MemberAddedEvent event)
    {
        final String contract = normalizeAddress(event.electionAddress());
        if (contract == null)
        {
            log.warn("MemberAdded ignored: invalid contract address. addr={}, tx={}", event.electionAddress(), event.txHash());
            return;
        }

        Election election = electionRepository.findByContractAddressIgnoreCase(contract).orElse(null);
        if (election == null)
        {
            log.warn("MemberAdded for unknown contract={}. tx={}", contract, event.txHash());
            return;
        }

        if (event.groupId() == null || !event.groupId().equals(election.getExternalNullifier()))
        {
            log.warn("MemberAdded groupId mismatch for contract={}. dbGroupId={}, eventGroupId={}, tx={}",
                    contract, election.getExternalNullifier(), event.groupId(), event.txHash());
            return;
        }

        String identityCommitment = event.identityCommitment() == null ? null : event.identityCommitment().toString();
        if (identityCommitment == null)
        {
            log.warn("MemberAdded without identityCommitment for contract={}. tx={}", contract, event.txHash());
            return;
        }

        VoterCommitment commitment = voterCommitmentRepository
                .findByElectionPublicIdAndIdentityCommitment(election.getPublicId(), identityCommitment)
                .orElse(null);
        if (commitment == null)
        {
            log.warn("MemberAdded for unknown identityCommitment. electionUuid={}, contract={}, commitment={}, tx={}",
                    election.getPublicId(), contract, identityCommitment, event.txHash());
            return;
        }

        commitment.setStatus(CommitmentStatus.ON_CHAIN);
        commitment.setMerkleLeafIndex(toLongOrNull(event.index()));
        if (event.txHash() != null && !event.txHash().isBlank())
        {
            commitment.setTransactionHash(event.txHash());
        }

        participationRepository.findByCitizenKeycloakIdAndElectionPublicId(
                        commitment.getCitizen().getKeycloakId(),
                        election.getPublicId()
                )
                .ifPresentOrElse(
                        participation ->
                        {
                            if (participation.getStatus() != ParticipationStatus.CAST)
                            {
                                participation.setStatus(ParticipationStatus.REGISTERED);
                            }
                        },
                        () ->
                        {
                            var participation = new org.krino.voting_system.entity.CitizenElectionParticipation();
                            participation.setCitizen(commitment.getCitizen());
                            participation.setElection(election);
                            participation.setStatus(ParticipationStatus.REGISTERED);
                            participationRepository.save(participation);
                        }
                );

        log.info("Reconciled voter commitment electionUuid={} citizenUuid={} commitment={} leafIndex={} tx={}",
                election.getPublicId(),
                commitment.getCitizen().getKeycloakId(),
                identityCommitment,
                commitment.getMerkleLeafIndex(),
                event.txHash());
    }

    @Override
    @Transactional
    public void onElectionStarted(ElectionStartedEvent event)
    {
        final String contract = normalizeAddress(event.electionAddress());
        if (contract == null)
        {
            log.warn("ElectionStarted ignored: invalid contract address. addr={}, tx={}", event.electionAddress(), event.txHash());
            return;
        }

        Election election = electionRepository.findByContractAddressIgnoreCase(contract).orElse(null);
        if (election == null)
        {
            log.warn("ElectionStarted for unknown contract={}. tx={}", contract, event.txHash());
            return;
        }

        Instant chainStart = toInstantSeconds(event.startTimeSeconds());
        Instant chainEnd = toInstantSeconds(event.endTimeSeconds());

        if (chainStart != null)
        {
            election.setStartTime(chainStart);
        }
        if (chainEnd != null)
        {
            election.setEndTime(chainEnd);
        }

        if (election.getPhase() != ElectionPhase.VOTING)
        {
            election.setPhase(ElectionPhase.VOTING);
            log.info("Marked election uuid={} as VOTING from contract={} at block={} logIndex={} tx={}",
                    election.getPublicId(), contract, event.blockNumber(), event.logIndex(), event.txHash());
        }
    }

    @Override
    @Transactional
    public void onElectionEnded(ElectionEndedEvent event)
    {
        final String contract = normalizeAddress(event.electionAddress());
        if (contract == null)
        {
            log.warn("ElectionEnded ignored: invalid contract address. addr={}, tx={}", event.electionAddress(), event.txHash());
            return;
        }

        Election election = electionRepository.findByContractAddressIgnoreCase(contract).orElse(null);
        if (election == null)
        {
            log.warn("ElectionEnded for unknown contract={}. tx={}", contract, event.txHash());
            return;
        }

        if (event.decryptionMaterial() != null && event.decryptionMaterial().length > 0)
        {
            election.setDecryptionMaterial(event.decryptionMaterial());
        }

        if (election.getPhase() != ElectionPhase.TALLY)
        {
            election.setPhase(ElectionPhase.TALLY);
            log.info("Marked election uuid={} as TALLY from contract={} at block={} logIndex={} tx={}",
                    election.getPublicId(), contract, event.blockNumber(), event.logIndex(), event.txHash());
        }
    }

    private static Instant toInstantSeconds(BigInteger epochSeconds)
    {
        if (epochSeconds == null) return null;
        try
        {
            return Instant.ofEpochSecond(epochSeconds.longValueExact());
        } catch (ArithmeticException ex)
        {
            return null;
        }
    }

    private static String normalizeAddress(String addr)
    {
        if (addr == null) return null;
        String a = addr.trim();
        if (!a.startsWith("0x")) a = "0x" + a;
        if (a.length() != 42) return null;
        return a.toLowerCase();
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
}
