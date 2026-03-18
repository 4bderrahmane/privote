package org.krino.voting_system.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.krino.voting_system.dto.citizen.CitizenSyncRequest;
import org.krino.voting_system.entity.Citizen;
import org.krino.voting_system.exception.ResourceNotFoundException;
import org.krino.voting_system.mapper.CitizenMapper;
import org.krino.voting_system.repository.CitizenRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Transactional
@Service
@RequiredArgsConstructor
public class CitizenService
{
    private final CitizenRepository citizenRepository;
    private final CitizenMapper citizenMapper;

    public List<Citizen> getAllCitizens()
    {
        return citizenRepository.findAllByIsDeletedFalse();
    }

    public Citizen getCitizenByUUID(UUID uuid)
    {
        return citizenRepository.findByKeycloakIdAndIsDeletedFalse(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Citizen not found with UUID: " + uuid));
    }

    public void sync(CitizenSyncRequest req)
    {
        if (req.keycloakId() == null) throw new IllegalArgumentException("keycloakId is required");
        if (req.cin() == null || req.cin().isBlank()) throw new IllegalArgumentException("cin is required");
        if (req.email() == null || req.email().isBlank()) throw new IllegalArgumentException("email is required");
        if (req.firstName() == null || req.firstName().isBlank())
            throw new IllegalArgumentException("firstName is required");
        if (req.lastName() == null || req.lastName().isBlank())
            throw new IllegalArgumentException("lastName is required");

        var existing = citizenRepository.findByKeycloakId(req.keycloakId());
        if (citizenRepository.existsByCinAndKeycloakIdNot(req.cin(), req.keycloakId()))
        {
            throw new IllegalStateException("CIN already used by another account");
        }

        if (existing.isPresent())
        {
            Citizen c = existing.get();
            citizenMapper.updateEntity(req, c);

            citizenRepository.save(c);
            return;
        }

        Citizen created = citizenMapper.toEntity(req);

        citizenRepository.save(created);
    }

    public void softDeleteCitizenByUUID(UUID uuid)
    {
        Citizen citizen = citizenRepository.findByKeycloakId(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Citizen not found with UUID: " + uuid));
        citizen.setDeleted(true);
        citizenRepository.save(citizen);
    }
}
