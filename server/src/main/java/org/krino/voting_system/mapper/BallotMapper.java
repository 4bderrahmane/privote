package org.krino.voting_system.mapper;

import org.krino.voting_system.dto.ballot.BallotCreateDto;
import org.krino.voting_system.entity.Ballot;

public interface BallotMapper
{
    Ballot toEntity(BallotCreateDto dto);

    void updateEntity(BallotCreateDto dto, Ballot ballot);
}
