package org.privote.backend.mapper;

import org.privote.backend.dto.citizen.CitizenSyncRequest;
import org.privote.backend.entity.Citizen;
import org.springframework.stereotype.Component;

@Component
public class CitizenMapperImpl implements CitizenMapper
{
    @Override
    public Citizen toEntity(CitizenSyncRequest request)
    {
        Citizen citizen = new Citizen();
        citizen.setKeycloakId(request.keycloakId());
        updateCommonFields(request, citizen);
        return citizen;
    }

    @Override
    public void updateEntity(CitizenSyncRequest request, Citizen citizen)
    {
        updateCommonFields(request, citizen);
    }

    private void updateCommonFields(CitizenSyncRequest request, Citizen citizen)
    {
        citizen.setUsername(request.username());
        citizen.setEmail(request.email());
        citizen.setFirstName(request.firstName());
        citizen.setLastName(request.lastName());
        citizen.setCin(request.cin());
        citizen.setBirthDate(request.birthDate());
        citizen.setBirthPlace(request.birthPlace());
        citizen.setPhoneNumber(request.phoneNumber());
        citizen.setEmailVerified(request.emailVerified());
        citizen.setDeleted(!request.enabled());
    }
}
