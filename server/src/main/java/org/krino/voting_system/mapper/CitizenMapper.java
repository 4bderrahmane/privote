package org.krino.voting_system.mapper;

import org.krino.voting_system.dto.citizen.CitizenSyncRequest;
import org.krino.voting_system.entity.Citizen;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CitizenMapper
{
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "keycloakId", source = "keycloakId")
    @Mapping(target = "address", ignore = true)
    @Mapping(target = "region", ignore = true)
    @Mapping(target = "isEligible", ignore = true)
    @Mapping(target = "voterCommitments", ignore = true)
    @Mapping(target = "candidacies", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", expression = "java(!request.enabled())")
    Citizen toEntity(CitizenSyncRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "keycloakId", ignore = true)
    @Mapping(target = "address", ignore = true)
    @Mapping(target = "region", ignore = true)
    @Mapping(target = "isEligible", ignore = true)
    @Mapping(target = "voterCommitments", ignore = true)
    @Mapping(target = "candidacies", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", expression = "java(!request.enabled())")
    void updateEntity(CitizenSyncRequest request, @MappingTarget Citizen citizen);
}
