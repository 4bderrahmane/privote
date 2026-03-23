package org.privote.backend.mapper;

import org.privote.backend.dto.election.ElectionCreateDto;
import org.privote.backend.dto.election.ElectionPatchDto;
import org.privote.backend.entity.Election;

public interface ElectionMapper
{
    Election toEntity(ElectionCreateDto dto);

    void updateEntity(ElectionCreateDto dto, Election election);

    void patchEntity(ElectionPatchDto dto, Election election);
}
