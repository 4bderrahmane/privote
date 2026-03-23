package org.privote.backend.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.privote.backend.dto.candidate.CandidateCreateByCinDto;
import org.privote.backend.dto.candidate.CandidateCreateDto;
import org.privote.backend.dto.candidate.CandidatePatchDto;
import org.privote.backend.dto.candidate.CandidateResponseDto;
import org.privote.backend.entity.Candidate;
import org.privote.backend.entity.Citizen;
import org.privote.backend.entity.Election;
import org.privote.backend.entity.Party;
import org.privote.backend.entity.enums.CandidateStatus;
import org.privote.backend.entity.enums.ElectionPhase;
import org.privote.backend.exception.ResourceNotFoundException;
import org.privote.backend.mapper.CandidateMapper;
import org.privote.backend.repository.CandidateRepository;
import org.privote.backend.repository.CitizenRepository;
import org.privote.backend.repository.ElectionRepository;
import org.privote.backend.repository.PartyRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Transactional
@Service
@RequiredArgsConstructor
public class CandidateService
{
    private final CandidateRepository candidateRepository;
    private final CitizenRepository citizenRepository;
    private final ElectionRepository electionRepository;
    private final PartyRepository partyRepository;
    private final CandidateMapper candidateMapper;

    public List<CandidateResponseDto> getAllCandidates()
    {
        return candidateRepository.findAll().stream()
                .map(CandidateResponseDto::fromEntity)
                .toList();
    }

    public List<CandidateResponseDto> getCandidatesByElectionPublicId(UUID electionPublicId)
    {
        resolveElection(electionPublicId);
        return candidateRepository.findByElectionPublicId(electionPublicId).stream()
                .map(CandidateResponseDto::fromEntity)
                .toList();
    }

    public List<CandidateResponseDto> getActiveCandidatesByElectionPublicId(UUID electionPublicId)
    {
        resolveElection(electionPublicId);
        return candidateRepository.findByElectionPublicIdAndStatus(
                        electionPublicId,
                        CandidateStatus.ACTIVE
                ).stream()
                .map(CandidateResponseDto::fromEntity)
                .toList();
    }

    public CandidateResponseDto getCandidateByPublicId(UUID publicId)
    {
        return CandidateResponseDto.fromEntity(getRequiredCandidate(publicId));
    }

    public CandidateResponseDto createCandidate(CandidateCreateDto candidateDto)
    {
        validateCreateDto(candidateDto);
        ensureCandidacyAvailable(candidateDto.getElectionPublicId(), candidateDto.getCitizenPublicId(), null);

        Election election = resolveElection(candidateDto.getElectionPublicId());
        requireRegistrationPhase(election);
        Candidate candidate = candidateMapper.toEntity(candidateDto);
        applyRelations(candidate, resolveCitizen(candidateDto.getCitizenPublicId()), election, resolveParty(candidateDto.getPartyPublicId()));
        return CandidateResponseDto.fromEntity(candidateRepository.save(candidate));
    }

    public CandidateResponseDto createCandidateByCin(UUID electionPublicId, CandidateCreateByCinDto candidateDto)
    {
        validateCreateByCinDto(candidateDto);

        Election election = resolveElection(electionPublicId);
        requireRegistrationPhase(election);
        Citizen citizen = resolveCitizenByCin(candidateDto.getCitizenCin());
        ensureCandidacyAvailable(electionPublicId, citizen.getKeycloakId(), null);

        Candidate candidate = new Candidate();
        candidate.setElection(election);
        candidate.setCitizen(citizen);
        candidate.setParty(resolveParty(candidateDto.getPartyPublicId()));
        validatePartyMembership(candidate.getParty(), citizen);
        if (candidateDto.getStatus() != null)
        {
            candidate.setStatus(candidateDto.getStatus());
        }

        return CandidateResponseDto.fromEntity(candidateRepository.save(candidate));
    }

    public CandidateResponseDto updateCandidate(UUID publicId, CandidateCreateDto candidateDto)
    {
        validateCreateDto(candidateDto);

        Candidate candidate = getRequiredCandidate(publicId);
        requireRegistrationPhase(candidate.getElection());
        candidateMapper.updateEntity(candidateDto, candidate);

        Election election = resolveElection(candidateDto.getElectionPublicId());
        requireRegistrationPhase(election);
        ensureCandidacyAvailable(candidateDto.getElectionPublicId(), candidateDto.getCitizenPublicId(), publicId);
        applyRelations(
                candidate,
                resolveCitizen(candidateDto.getCitizenPublicId()),
                election,
                resolveParty(candidateDto.getPartyPublicId())
        );
        return CandidateResponseDto.fromEntity(candidateRepository.save(candidate));
    }

    public CandidateResponseDto patchCandidate(UUID publicId, CandidatePatchDto patchDto)
    {
        if (patchDto == null)
        {
            throw new IllegalArgumentException("Candidate patch payload is required");
        }

        Candidate candidate = getRequiredCandidate(publicId);
        requireRegistrationPhase(candidate.getElection());
        UUID effectiveCitizenPublicId = patchDto.getCitizenPublicId() != null
                ? patchDto.getCitizenPublicId()
                : candidate.getCitizen().getKeycloakId();
        UUID effectiveElectionPublicId = patchDto.getElectionPublicId() != null
                ? patchDto.getElectionPublicId()
                : candidate.getElection().getPublicId();

        Election election = resolveElection(effectiveElectionPublicId);
        requireRegistrationPhase(election);
        ensureCandidacyAvailable(effectiveElectionPublicId, effectiveCitizenPublicId, publicId);

        candidateMapper.patchEntity(patchDto, candidate);
        applyRelations(
                candidate,
                resolveCitizen(effectiveCitizenPublicId),
                election,
                resolveParty(resolveEffectivePartyPublicId(candidate, patchDto))
        );
        return CandidateResponseDto.fromEntity(candidateRepository.save(candidate));
    }

    public void deleteCandidateByPublicId(UUID publicId)
    {
        Candidate candidate = getRequiredCandidate(publicId);
        requireRegistrationPhase(candidate.getElection());
        candidateRepository.delete(candidate);
    }

    private Candidate getRequiredCandidate(UUID publicId)
    {
        return candidateRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(Candidate.class.getSimpleName(), "UUID", publicId));
    }

    private void validateCreateDto(CandidateCreateDto candidateDto)
    {
        if (candidateDto == null)
        {
            throw new IllegalArgumentException("Candidate payload is required");
        }
        if (candidateDto.getCitizenPublicId() == null)
        {
            throw new IllegalArgumentException("citizenPublicId is required");
        }
        if (candidateDto.getElectionPublicId() == null)
        {
            throw new IllegalArgumentException("electionPublicId is required");
        }
    }

    private void validateCreateByCinDto(CandidateCreateByCinDto candidateDto)
    {
        if (candidateDto == null)
        {
            throw new IllegalArgumentException("Candidate payload is required");
        }
        if (candidateDto.getCitizenCin() == null || candidateDto.getCitizenCin().isBlank())
        {
            throw new IllegalArgumentException("citizenCin is required");
        }
    }

    private void ensureCandidacyAvailable(UUID electionPublicId, UUID citizenPublicId, UUID currentCandidatePublicId)
    {
        boolean exists = currentCandidatePublicId == null
                ? candidateRepository.existsByElectionPublicIdAndCitizenKeycloakId(electionPublicId, citizenPublicId)
                : candidateRepository.existsByElectionPublicIdAndCitizenKeycloakIdAndPublicIdNot(
                        electionPublicId,
                        citizenPublicId,
                        currentCandidatePublicId
                );

        if (exists)
        {
            throw new IllegalStateException("Citizen is already registered as a candidate for this election");
        }
    }

    private void applyRelations(Candidate candidate, Citizen citizen, Election election, @Nullable Party party)
    {
        candidate.setCitizen(citizen);
        candidate.setElection(election);
        candidate.setParty(party);
        validatePartyMembership(party, citizen);
    }

    private UUID resolveEffectivePartyPublicId(Candidate candidate, CandidatePatchDto patchDto)
    {
        if (patchDto.getPartyPublicId() != null)
        {
            return patchDto.getPartyPublicId();
        }
        return candidate.getParty() == null ? null : candidate.getParty().getPublicId();
    }

    private Citizen resolveCitizen(UUID citizenPublicId)
    {
        return citizenRepository.findByKeycloakIdAndIsDeletedFalse(citizenPublicId)
                .orElseThrow(() -> new ResourceNotFoundException(Citizen.class.getSimpleName(), "UUID", citizenPublicId));
    }

    private Citizen resolveCitizenByCin(String citizenCin)
    {
        String normalizedCin = citizenCin.trim();
        return citizenRepository.findByCinAndIsDeletedFalse(normalizedCin)
                .orElseThrow(() -> new ResourceNotFoundException(Citizen.class.getSimpleName(), "cin", normalizedCin));
    }

    private Election resolveElection(UUID electionPublicId)
    {
        return electionRepository.findByPublicId(electionPublicId)
                .orElseThrow(() -> new ResourceNotFoundException(Election.class.getSimpleName(), "UUID", electionPublicId));
    }

    private Party resolveParty(UUID partyPublicId)
    {
        if (partyPublicId == null)
        {
            return null;
        }

        return partyRepository.findByPublicId(partyPublicId)
                .orElseThrow(() -> new ResourceNotFoundException(Party.class.getSimpleName(), "UUID", partyPublicId));
    }

    private void validatePartyMembership(@Nullable Party party, Citizen citizen)
    {
        if (party == null)
        {
            return;
        }

        boolean member = party.getMembers().stream()
                .anyMatch(candidateCitizen -> citizen.getKeycloakId().equals(candidateCitizen.getKeycloakId()));

        if (!member)
        {
            throw new IllegalStateException("Citizen must be a member of the selected party");
        }
    }

    private void requireRegistrationPhase(Election election)
    {
        if (election.getPhase() != ElectionPhase.REGISTRATION)
        {
            throw new IllegalStateException("Candidates can only be managed while the election is in REGISTRATION");
        }
    }
}
