package org.krino.voting_system.mapper;

import org.krino.voting_system.dto.ballot.BallotCreateDto;
import org.krino.voting_system.entity.Ballot;
import org.springframework.stereotype.Component;

@Component
public class BallotMapperImpl implements BallotMapper
{
    @Override
    public Ballot toEntity(BallotCreateDto dto)
    {
        Ballot ballot = new Ballot();
        updateEntity(dto, ballot);
        return ballot;
    }

    @Override
    public void updateEntity(BallotCreateDto dto, Ballot ballot)
    {
        ballot.setCiphertext(dto.getCiphertext());
        ballot.setCiphertextHash(dto.getCiphertextHash());
        ballot.setNullifier(dto.getNullifier());
        ballot.setZkProof(dto.getZkProof());
        ballot.setTransactionHash(dto.getTransactionHash());
        ballot.setBlockNumber(dto.getBlockNumber());
    }
}
