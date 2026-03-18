package org.krino.voting_system.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.krino.voting_system.dto.candidate.CandidateCreateDto;
import org.krino.voting_system.dto.candidate.CandidatePatchDto;
import org.krino.voting_system.entity.Candidate;
import org.krino.voting_system.entity.Citizen;
import org.krino.voting_system.entity.Election;
import org.krino.voting_system.entity.Party;
import org.krino.voting_system.exception.ResourceNotFoundException;
import org.krino.voting_system.mapper.CandidateMapper;
import org.krino.voting_system.repository.CandidateRepository;
import org.krino.voting_system.repository.CitizenRepository;
import org.krino.voting_system.repository.ElectionRepository;
import org.krino.voting_system.repository.PartyRepository;
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

    public List<Candidate> getAllCandidates()
    {
        return candidateRepository.findAll();
    }

    public List<Candidate> getCandidatesByElectionPublicId(UUID electionPublicId)
    {
        return candidateRepository.findByElectionPublicId(electionPublicId);
    }

    public Candidate getCandidateByPublicId(UUID publicId)
    {
        return candidateRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(Candidate.class.getSimpleName(), "UUID", publicId));
    }

    public Candidate createCandidate(CandidateCreateDto candidateDto)
    {
        validateCreateDto(candidateDto);
        ensureCandidacyAvailable(candidateDto.getElectionPublicId(), candidateDto.getCitizenPublicId(), null);

        Candidate candidate = candidateMapper.toEntity(candidateDto);
        applyRelations(candidate, candidateDto.getCitizenPublicId(), candidateDto.getElectionPublicId(), candidateDto.getPartyPublicId());
        return candidateRepository.save(candidate);
    }

    public Candidate updateCandidate(UUID publicId, CandidateCreateDto candidateDto)
    {
        validateCreateDto(candidateDto);
        ensureCandidacyAvailable(candidateDto.getElectionPublicId(), candidateDto.getCitizenPublicId(), publicId);

        Candidate candidate = getRequiredCandidate(publicId);
        candidateMapper.updateEntity(candidateDto, candidate);
        applyRelations(candidate, candidateDto.getCitizenPublicId(), candidateDto.getElectionPublicId(), candidateDto.getPartyPublicId());
        return candidateRepository.save(candidate);
    }

    public Candidate patchCandidate(UUID publicId, CandidatePatchDto patchDto)
    {
        if (patchDto == null)
        {
            throw new IllegalArgumentException("Candidate patch payload is required");
        }

        Candidate candidate = getRequiredCandidate(publicId);
        UUID effectiveCitizenPublicId = patchDto.getCitizenPublicId() != null
                ? patchDto.getCitizenPublicId()
                : candidate.getCitizen().getKeycloakId();
        UUID effectiveElectionPublicId = patchDto.getElectionPublicId() != null
                ? patchDto.getElectionPublicId()
                : candidate.getElection().getPublicId();

        ensureCandidacyAvailable(effectiveElectionPublicId, effectiveCitizenPublicId, publicId);

        candidateMapper.patchEntity(patchDto, candidate);
        applyRelations(candidate, effectiveCitizenPublicId, effectiveElectionPublicId, resolveEffectivePartyPublicId(candidate, patchDto));
        return candidateRepository.save(candidate);
    }

    public void deleteCandidateByPublicId(UUID publicId)
    {
        Candidate candidate = getRequiredCandidate(publicId);
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

    private void applyRelations(Candidate candidate, UUID citizenPublicId, UUID electionPublicId, UUID partyPublicId)
    {
        candidate.setCitizen(resolveCitizen(citizenPublicId));
        candidate.setElection(resolveElection(electionPublicId));
        candidate.setParty(resolveParty(partyPublicId));
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
}
