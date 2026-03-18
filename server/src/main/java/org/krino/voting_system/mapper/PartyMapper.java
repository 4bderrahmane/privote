package org.krino.voting_system.mapper;

import org.krino.voting_system.dto.party.PartyCreateDto;
import org.krino.voting_system.dto.party.PartyPatchDto;
import org.krino.voting_system.entity.Party;

public interface PartyMapper
{
    Party toEntity(PartyCreateDto dto);

    void updateEntity(PartyCreateDto dto, Party party);

    void patchEntity(PartyPatchDto dto, Party party);
}
