package org.privote.backend.mapper;

import org.privote.backend.dto.citizen.CitizenSyncRequest;
import org.privote.backend.entity.Citizen;

public interface CitizenMapper
{
    Citizen toEntity(CitizenSyncRequest request);

    void updateEntity(CitizenSyncRequest request, Citizen citizen);
}
