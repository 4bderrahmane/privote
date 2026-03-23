package org.privote.backend.mapper;

import org.privote.backend.dto.candidate.CandidateCreateDto;
import org.privote.backend.dto.candidate.CandidatePatchDto;
import org.privote.backend.entity.Candidate;

public interface CandidateMapper
{
    Candidate toEntity(CandidateCreateDto dto);

    void updateEntity(CandidateCreateDto dto, Candidate candidate);

    void patchEntity(CandidatePatchDto dto, Candidate candidate);
}
