package org.krino.voting_system.mapper;

import org.krino.voting_system.dto.citizen.CitizenSyncRequest;
import org.krino.voting_system.entity.Citizen;

public interface CitizenMapper
{
    Citizen toEntity(CitizenSyncRequest request);

    void updateEntity(CitizenSyncRequest request, Citizen citizen);
}
