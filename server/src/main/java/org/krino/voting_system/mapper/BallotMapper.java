package org.krino.voting_system.mapper;

import org.krino.voting_system.dto.ballot.BallotCreateDto;
import org.krino.voting_system.entity.Ballot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface BallotMapper
{
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "election", ignore = true)
    @Mapping(target = "candidate", ignore = true)
    @Mapping(target = "castAt", ignore = true)
    Ballot toEntity(BallotCreateDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "election", ignore = true)
    @Mapping(target = "candidate", ignore = true)
    @Mapping(target = "castAt", ignore = true)
    void updateEntity(BallotCreateDto dto, @MappingTarget Ballot ballot);
}
