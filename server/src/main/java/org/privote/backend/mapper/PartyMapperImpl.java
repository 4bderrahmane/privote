package org.privote.backend.mapper;

import org.privote.backend.dto.party.PartyCreateDto;
import org.privote.backend.dto.party.PartyPatchDto;
import org.privote.backend.entity.Party;
import org.springframework.stereotype.Component;

@Component
public class PartyMapperImpl implements PartyMapper
{
    @Override
    public Party toEntity(PartyCreateDto dto)
    {
        Party party = new Party();
        updateEntity(dto, party);
        return party;
    }

    @Override
    public void updateEntity(PartyCreateDto dto, Party party)
    {
        party.setName(trimNullable(dto.getName()));
        party.setDescription(trimNullable(dto.getDescription()));
    }

    @Override
    public void patchEntity(PartyPatchDto dto, Party party)
    {
        if (dto.getName() != null)
        {
            party.setName(trimNullable(dto.getName()));
        }
        if (dto.getDescription() != null)
        {
            party.setDescription(trimNullable(dto.getDescription()));
        }
    }

    private String trimNullable(String value)
    {
        return value == null ? null : value.trim();
    }
}
