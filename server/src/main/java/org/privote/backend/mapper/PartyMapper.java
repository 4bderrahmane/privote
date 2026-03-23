package org.privote.backend.mapper;

import org.privote.backend.dto.party.PartyCreateDto;
import org.privote.backend.dto.party.PartyPatchDto;
import org.privote.backend.entity.Party;

public interface PartyMapper
{
    Party toEntity(PartyCreateDto dto);

    void updateEntity(PartyCreateDto dto, Party party);

    void patchEntity(PartyPatchDto dto, Party party);
}
