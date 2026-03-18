package org.krino.voting_system.mapper;

import org.krino.voting_system.dto.party.PartyCreateDto;
import org.krino.voting_system.dto.party.PartyPatchDto;
import org.krino.voting_system.entity.Party;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface PartyMapper
{
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "name", source = "name", qualifiedByName = "trimNullable")
    @Mapping(target = "description", source = "description", qualifiedByName = "trimNullable")
    Party toEntity(PartyCreateDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "name", source = "name", qualifiedByName = "trimNullable")
    @Mapping(target = "description", source = "description", qualifiedByName = "trimNullable")
    void updateEntity(PartyCreateDto dto, @MappingTarget Party party);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "name", source = "name", qualifiedByName = "trimNullable")
    @Mapping(target = "description", source = "description", qualifiedByName = "trimNullable")
    void patchEntity(PartyPatchDto dto, @MappingTarget Party party);

    @Named("trimNullable")
    default String trimNullable(String value)
    {
        return value == null ? null : value.trim();
    }
}
