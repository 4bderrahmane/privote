package org.krino.voting_system.mapper;

import org.krino.voting_system.dto.election.ElectionCreateDto;
import org.krino.voting_system.dto.election.ElectionPatchDto;
import org.krino.voting_system.entity.Election;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface ElectionMapper
{
    @Mapping(target = "title", source = "title", qualifiedByName = "trimNullable")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "contractAddress", ignore = true)

    @Mapping(target = "startTime", source = "startTime")
    @Mapping(target = "endTime", source = "endTime")
    @Mapping(target = "phase", source = "phase")

    // Required fields that are set by the service layer / web3 provisioning
    @Mapping(target = "externalNullifier", ignore = true)
    @Mapping(target = "coordinator", ignore = true)
    @Mapping(target = "encryptionPublicKey", source = "encryptionPublicKey")
    @Mapping(target = "decryptionMaterial", source = "decryptionKey")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Election toEntity(ElectionCreateDto dto);

    @Mapping(target = "title", source = "title", qualifiedByName = "trimNullable")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "contractAddress", ignore = true)
    @Mapping(target = "externalNullifier", ignore = true)
    @Mapping(target = "coordinator", ignore = true)
    @Mapping(target = "encryptionPublicKey", source = "encryptionPublicKey")
    @Mapping(target = "decryptionMaterial", source = "decryptionKey")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(ElectionCreateDto dto, @MappingTarget Election election);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "title", source = "title", qualifiedByName = "trimNullable")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "contractAddress", ignore = true)
    @Mapping(target = "externalNullifier", ignore = true)
    @Mapping(target = "coordinator", ignore = true)
    @Mapping(target = "encryptionPublicKey", source = "encryptionPublicKey")
    @Mapping(target = "decryptionMaterial", source = "decryptionKey")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void patchEntity(ElectionPatchDto dto, @MappingTarget Election election);

    @Named("trimNullable")
    default String trimNullable(String value)
    {
        return value == null ? null : value.trim();
    }
}
