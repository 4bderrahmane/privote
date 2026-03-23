package org.privote.backend.mapper;

import org.privote.backend.dto.ballot.BallotCreateDto;
import org.privote.backend.entity.Ballot;

public interface BallotMapper
{
    Ballot toEntity(BallotCreateDto dto);

    void updateEntity(BallotCreateDto dto, Ballot ballot);
}
