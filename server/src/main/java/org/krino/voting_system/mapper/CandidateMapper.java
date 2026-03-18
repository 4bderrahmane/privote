package org.krino.voting_system.mapper;

import org.krino.voting_system.dto.candidate.CandidateCreateDto;
import org.krino.voting_system.dto.candidate.CandidatePatchDto;
import org.krino.voting_system.entity.Candidate;

public interface CandidateMapper
{
    Candidate toEntity(CandidateCreateDto dto);

    void updateEntity(CandidateCreateDto dto, Candidate candidate);

    void patchEntity(CandidatePatchDto dto, Candidate candidate);
}
