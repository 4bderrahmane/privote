package org.krino.voting_system.mapper;

import org.krino.voting_system.dto.election.ElectionCreateDto;
import org.krino.voting_system.dto.election.ElectionPatchDto;
import org.krino.voting_system.entity.Election;

public interface ElectionMapper
{
    Election toEntity(ElectionCreateDto dto);

    void updateEntity(ElectionCreateDto dto, Election election);

    void patchEntity(ElectionPatchDto dto, Election election);
}
