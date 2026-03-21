package org.krino.voting_system.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.krino.voting_system.dto.election.ElectionCreateDto;
import org.krino.voting_system.dto.election.ElectionPatchDto;
import org.krino.voting_system.entity.Citizen;
import org.krino.voting_system.entity.Election;
import org.krino.voting_system.entity.enums.ElectionPhase;
import org.krino.voting_system.exception.ResourceNotFoundException;
import org.krino.voting_system.mapper.ElectionMapper;
import org.krino.voting_system.repository.CitizenRepository;
import org.krino.voting_system.repository.ElectionRepository;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Transactional
@Service
@RequiredArgsConstructor
public class ElectionService
{
    public final ElectionRepository electionRepository;
    private final ElectionMapper electionMapper;
    private final CitizenRepository citizenRepository;

    public List<Election> findAllElections()
    {
        return electionRepository.findAll();
    }

    public Election getElectionById(Long id)
    {
        return electionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(Election.class.getSimpleName(), "id", id));
    }

    public Election getElectionByPublicId(UUID publicId)
    {
        return getRequiredElectionByPublicId(publicId);
    }

    public void deleteElectionByPublicId(UUID uuid)
    {
        Election election = electionRepository.findByPublicId(uuid)
                .orElseThrow(() -> new ResourceNotFoundException(Election.class.getSimpleName(), "UUID", uuid));
        electionRepository.delete(election);
    }

    public Election createElection(ElectionCreateDto electionDto)
    {
        validateElectionCreateDto(electionDto);
        validateTimeRange(electionDto.getStartTime(), electionDto.getEndTime());

        Election election = electionMapper.toEntity(electionDto);
        if (election.getPublicId() == null)
        {
            election.setPublicId(UUID.randomUUID());
        }

        if (election.getPhase() == null)
        {
            election.setPhase(ElectionPhase.REGISTRATION);
        }

        election.setExternalNullifier(resolveCanonicalExternalNullifier(election.getPublicId(), electionDto.getExternalNullifier()));

        var coordinator = citizenRepository.findByKeycloakId(electionDto.getCoordinatorKeycloakId())
                .orElseThrow(() -> new ResourceNotFoundException("Citizen", "keycloakId", electionDto.getCoordinatorKeycloakId()));
        election.setCoordinator(coordinator);

        return electionRepository.save(election);
    }

    public Election patchElection(UUID publicId, ElectionPatchDto patchDto)
    {
        if (patchDto == null)
        {
            throw new IllegalArgumentException("Election patch payload is required");
        }

        Election election = getRequiredElectionByPublicId(publicId);
        boolean deployed = election.getContractAddress() != null && !election.getContractAddress().isBlank();

        if (patchDto.getTitle() != null)
        {
            if (patchDto.getTitle().isBlank())
            {
                throw new IllegalArgumentException("title cannot be blank");
            }
            election.setTitle(patchDto.getTitle().trim());
        }

        if (patchDto.getDescription() != null)
        {
            election.setDescription(patchDto.getDescription());
        }

        validateImmutableOnChainFields(election, patchDto, deployed);
        validateTimeRange(
                patchDto.getStartTime() != null ? patchDto.getStartTime() : election.getStartTime(),
                patchDto.getEndTime() != null ? patchDto.getEndTime() : election.getEndTime()
        );

        electionMapper.patchEntity(patchDto, election);

        if (patchDto.getCoordinatorKeycloakId() != null)
        {
            election.setCoordinator(resolveCoordinator(patchDto.getCoordinatorKeycloakId()));
        }

        election.setExternalNullifier(resolveCanonicalExternalNullifier(election.getPublicId(), patchDto.getExternalNullifier()));

        return electionRepository.save(election);
    }

    public Election updateElection(UUID publicId, ElectionCreateDto electionDto)
    {
        validateElectionCreateDto(electionDto);

        Election election = getRequiredElectionByPublicId(publicId);
        validateTimeRange(electionDto.getStartTime(), electionDto.getEndTime());

        boolean deployed = election.getContractAddress() != null && !election.getContractAddress().isBlank();
        if (deployed)
        {
            validateImmutableOnChainFields(election, electionDto);
        }

        ElectionPhase existingPhase = election.getPhase();
        electionMapper.updateEntity(electionDto, election);

        if (electionDto.getPhase() != null)
        {
            election.setPhase(electionDto.getPhase());
        } else if (existingPhase != null)
        {
            election.setPhase(existingPhase);
        } else if (election.getPhase() == null)
        {
            election.setPhase(ElectionPhase.REGISTRATION);
        }

        election.setExternalNullifier(resolveCanonicalExternalNullifier(election.getPublicId(), electionDto.getExternalNullifier()));

        election.setCoordinator(resolveCoordinator(electionDto.getCoordinatorKeycloakId()));

        return electionRepository.save(election);
    }

    private static void validateElectionCreateDto(ElectionCreateDto electionDto)
    {
        if (electionDto == null)
        {
            throw new IllegalArgumentException("Election payload is required");
        }
        if (electionDto.getTitle() == null || electionDto.getTitle().isBlank())
        {
            throw new IllegalArgumentException("title is required");
        }
        if (electionDto.getEndTime() == null)
        {
            throw new IllegalArgumentException("endTime is required");
        }
        if (electionDto.getCoordinatorKeycloakId() == null)
        {
            throw new IllegalArgumentException("coordinatorKeycloakId is required");
        }
        if (electionDto.getEncryptionPublicKey() == null || electionDto.getEncryptionPublicKey().length == 0)
        {
            throw new IllegalArgumentException("encryptionPublicKey is required");
        }
    }

    private Election getRequiredElectionByPublicId(UUID publicId)
    {
        return electionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(Election.class.getSimpleName(), "UUID", publicId));
    }

    private void validateImmutableOnChainFields(Election election, ElectionCreateDto electionDto)
    {
        if (electionDto.getExternalNullifier() != null
                && !electionDto.getExternalNullifier().equals(election.getExternalNullifier()))
        {
            throw new IllegalArgumentException("externalNullifier cannot be changed after contract deployment");
        }

        if (!java.util.Arrays.equals(election.getEncryptionPublicKey(), electionDto.getEncryptionPublicKey()))
        {
            throw new IllegalArgumentException("encryptionPublicKey cannot be changed after contract deployment");
        }
    }

    private void validateImmutableOnChainFields(Election election, ElectionPatchDto patchDto, boolean deployed)
    {
        if (patchDto.getEncryptionPublicKey() != null && patchDto.getEncryptionPublicKey().length == 0)
        {
            throw new IllegalArgumentException("encryptionPublicKey cannot be empty");
        }

        if (!deployed)
        {
            return;
        }

        if (patchDto.getExternalNullifier() != null
                && !patchDto.getExternalNullifier().equals(election.getExternalNullifier()))
        {
            throw new IllegalArgumentException("externalNullifier cannot be changed after contract deployment");
        }

        if (patchDto.getEncryptionPublicKey() != null
                && !java.util.Arrays.equals(election.getEncryptionPublicKey(), patchDto.getEncryptionPublicKey()))
        {
            throw new IllegalArgumentException("encryptionPublicKey cannot be changed after contract deployment");
        }
    }

    private static void validateTimeRange(Instant start, Instant end)
    {
        if (end == null)
        {
            throw new IllegalArgumentException("endTime is required");
        }

        if (start != null && end.isBefore(start))
        {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
    }

    private Citizen resolveCoordinator(UUID coordinatorKeycloakId)
    {
        return citizenRepository.findByKeycloakId(coordinatorKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Citizen", "keycloakId", coordinatorKeycloakId));
    }

    public static BigInteger deriveExternalNullifier(UUID publicId)
    {
        if (publicId == null)
        {
            throw new IllegalArgumentException("publicId is required");
        }

        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(publicId.getMostSignificantBits());
        buffer.putLong(publicId.getLeastSignificantBits());
        return new BigInteger(1, buffer.array());
    }

    private static BigInteger resolveCanonicalExternalNullifier(UUID publicId, BigInteger requested)
    {
        BigInteger canonical = deriveExternalNullifier(publicId);
        if (requested != null && !canonical.equals(requested))
        {
            throw new IllegalArgumentException("externalNullifier must match the canonical UUID-derived election scope");
        }
        return canonical;
    }
}
