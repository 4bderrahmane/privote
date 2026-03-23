package org.privote.backend.service;

import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.privote.backend.dto.party.PartyCreateDto;
import org.privote.backend.dto.party.PartyPatchDto;
import org.privote.backend.entity.Citizen;
import org.privote.backend.entity.Party;
import org.privote.backend.exception.ResourceNotFoundException;
import org.privote.backend.repository.CitizenRepository;
import org.privote.backend.mapper.PartyMapper;
import org.privote.backend.repository.PartyRepository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Transactional
@Service
@RequiredArgsConstructor
public class PartyService
{
    private final PartyRepository partyRepository;
    private final PartyMapper partyMapper;
    private final CitizenRepository citizenRepository;

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
        validatePartyCreateDto(partyDto, true);
        ensurePartyNameAvailable(partyDto.getName(), null);

        Party party = partyMapper.toEntity(partyDto);
        party.setMembers(resolveMembersByCin(partyDto.getMemberCins(), true));
        return partyRepository.save(party);
    }

    public Party updateParty(UUID publicId, PartyCreateDto partyDto)
    {
        validatePartyCreateDto(partyDto, true);
        ensurePartyNameAvailable(partyDto.getName(), publicId);

        Party party = getRequiredParty(publicId);
        partyMapper.updateEntity(partyDto, party);
        party.setMembers(resolveMembersByCin(partyDto.getMemberCins(), true));
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

    private void validatePartyCreateDto(PartyCreateDto partyDto, boolean requireMembers)
    {
        if (partyDto == null)
        {
            throw new IllegalArgumentException("Party payload is required");
        }
        if (partyDto.getName() == null || partyDto.getName().isBlank())
        {
            throw new IllegalArgumentException("name is required");
        }

        if (requireMembers && (partyDto.getMemberCins() == null || partyDto.getMemberCins().isEmpty()))
        {
            throw new IllegalArgumentException("at least one member CIN is required");
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

    private List<Citizen> resolveMembersByCin(List<String> memberCins, boolean requireMembers)
    {
        if (memberCins == null || memberCins.isEmpty())
        {
            if (requireMembers)
            {
                throw new IllegalArgumentException("at least one member CIN is required");
            }
            return new ArrayList<>();
        }

        var uniqueCins = new LinkedHashSet<String>();
        for (String rawCin : memberCins)
        {
            if (rawCin == null || rawCin.isBlank())
            {
                continue;
            }
            uniqueCins.add(rawCin.trim());
        }

        if (uniqueCins.isEmpty() && requireMembers)
        {
            throw new IllegalArgumentException("at least one member CIN is required");
        }

        var members = new ArrayList<Citizen>();
        for (String cin : uniqueCins)
        {
            Citizen citizen = citizenRepository.findByCinAndIsDeletedFalse(cin)
                    .orElseThrow(() -> new ResourceNotFoundException("Citizen", "cin", cin));
            members.add(citizen);
        }

        return members;
    }
}
