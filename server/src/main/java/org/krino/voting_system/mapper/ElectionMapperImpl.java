package org.krino.voting_system.mapper;

import org.krino.voting_system.dto.election.ElectionCreateDto;
import org.krino.voting_system.dto.election.ElectionPatchDto;
import org.krino.voting_system.entity.Election;
import org.springframework.stereotype.Component;

@Component
public class ElectionMapperImpl implements ElectionMapper
{
    @Override
    public Election toEntity(ElectionCreateDto dto)
    {
        Election election = new Election();
        updateEntity(dto, election);
        return election;
    }

    @Override
    public void updateEntity(ElectionCreateDto dto, Election election)
    {
        election.setTitle(trimNullable(dto.getTitle()));
        election.setDescription(dto.getDescription());
        election.setStartTime(dto.getStartTime());
        election.setEndTime(dto.getEndTime());
        election.setPhase(dto.getPhase());
        election.setEncryptionPublicKey(dto.getEncryptionPublicKey());
        election.setDecryptionMaterial(dto.getDecryptionKey());
    }

    @Override
    public void patchEntity(ElectionPatchDto dto, Election election)
    {
        if (dto.getTitle() != null)
        {
            election.setTitle(trimNullable(dto.getTitle()));
        }
        if (dto.getDescription() != null)
        {
            election.setDescription(dto.getDescription());
        }
        if (dto.getStartTime() != null)
        {
            election.setStartTime(dto.getStartTime());
        }
        if (dto.getEndTime() != null)
        {
            election.setEndTime(dto.getEndTime());
        }
        if (dto.getPhase() != null)
        {
            election.setPhase(dto.getPhase());
        }
        if (dto.getEncryptionPublicKey() != null)
        {
            election.setEncryptionPublicKey(dto.getEncryptionPublicKey());
        }
        if (dto.getDecryptionKey() != null)
        {
            election.setDecryptionMaterial(dto.getDecryptionKey());
        }
    }

    private String trimNullable(String value)
    {
        return value == null ? null : value.trim();
    }
}
