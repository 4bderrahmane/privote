package org.krino.voting_system.mapper;

import org.krino.voting_system.dto.party.PartyCreateDto;
import org.krino.voting_system.dto.party.PartyPatchDto;
import org.krino.voting_system.entity.Party;
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
