package org.krino.voting_system.mapper;

import org.krino.voting_system.dto.candidate.CandidateCreateDto;
import org.krino.voting_system.dto.candidate.CandidatePatchDto;
import org.krino.voting_system.entity.Candidate;
import org.springframework.stereotype.Component;

@Component
public class CandidateMapperImpl implements CandidateMapper
{
    @Override
    public Candidate toEntity(CandidateCreateDto dto)
    {
        Candidate candidate = new Candidate();
        updateEntity(dto, candidate);
        return candidate;
    }

    @Override
    public void updateEntity(CandidateCreateDto dto, Candidate candidate)
    {
        candidate.setStatus(dto.getStatus());
    }

    @Override
    public void patchEntity(CandidatePatchDto dto, Candidate candidate)
    {
        if (dto.getStatus() != null)
        {
            candidate.setStatus(dto.getStatus());
        }
    }
}
