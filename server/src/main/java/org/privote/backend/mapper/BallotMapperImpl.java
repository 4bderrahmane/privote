package org.privote.backend.mapper;

import org.privote.backend.dto.ballot.BallotCreateDto;
import org.privote.backend.entity.Ballot;
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
