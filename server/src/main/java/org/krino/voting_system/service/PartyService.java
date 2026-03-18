package org.krino.voting_system.service;

import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.krino.voting_system.dto.party.PartyCreateDto;
import org.krino.voting_system.dto.party.PartyPatchDto;
import org.krino.voting_system.entity.Party;
import org.krino.voting_system.exception.ResourceNotFoundException;
import org.krino.voting_system.mapper.PartyMapper;
import org.krino.voting_system.repository.PartyRepository;

import java.util.List;
import java.util.UUID;

@Transactional
@Service
@RequiredArgsConstructor
public class PartyService
{
    private final PartyRepository partyRepository;
    private final PartyMapper partyMapper;

    public List<Party> findAllParties()
    {
        return partyRepository.findAll();
    }

    public Party getPartyByPublicId(UUID publicId)
    {
        return partyRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(Party.class.getSimpleName(), "UUID", publicId));
    }

    public Party createParty(PartyCreateDto partyDto)
    {
        validatePartyCreateDto(partyDto);
        ensurePartyNameAvailable(partyDto.getName(), null);

        Party party = partyMapper.toEntity(partyDto);
        return partyRepository.save(party);
    }

    public Party updateParty(UUID publicId, PartyCreateDto partyDto)
    {
        validatePartyCreateDto(partyDto);
        ensurePartyNameAvailable(partyDto.getName(), publicId);

        Party party = getRequiredParty(publicId);
        partyMapper.updateEntity(partyDto, party);
        return partyRepository.save(party);
    }

    public Party patchParty(UUID publicId, PartyPatchDto patchDto)
    {
        if (patchDto == null)
        {
            throw new IllegalArgumentException("Party patch payload is required");
        }
        if (patchDto.getName() != null)
        {
            if (patchDto.getName().isBlank())
            {
                throw new IllegalArgumentException("name cannot be blank");
            }
            ensurePartyNameAvailable(patchDto.getName(), publicId);
        }

        Party party = getRequiredParty(publicId);
        partyMapper.patchEntity(patchDto, party);
        return partyRepository.save(party);
    }

    public void deletePartyByPublicId(UUID publicId)
    {
        Party party = getRequiredParty(publicId);
        partyRepository.delete(party);
    }

    private Party getRequiredParty(UUID publicId)
    {
        return partyRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(Party.class.getSimpleName(), "UUID", publicId));
    }

    private void validatePartyCreateDto(PartyCreateDto partyDto)
    {
        if (partyDto == null)
        {
            throw new IllegalArgumentException("Party payload is required");
        }
        if (partyDto.getName() == null || partyDto.getName().isBlank())
        {
            throw new IllegalArgumentException("name is required");
        }
    }

    private void ensurePartyNameAvailable(String rawName, UUID currentPublicId)
    {
        String name = rawName.trim();
        boolean exists = currentPublicId == null
                ? partyRepository.existsByNameIgnoreCase(name)
                : partyRepository.existsByNameIgnoreCaseAndPublicIdNot(name, currentPublicId);

        if (exists)
        {
            throw new IllegalStateException("Party name already used");
        }
    }
}
