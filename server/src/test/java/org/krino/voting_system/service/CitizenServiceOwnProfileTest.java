package org.krino.voting_system.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.krino.voting_system.dto.citizen.CitizenSelfUpdateRequest;
import org.krino.voting_system.dto.citizen.CitizenSyncRequest;
import org.krino.voting_system.entity.Citizen;
import org.krino.voting_system.mapper.CitizenMapper;
import org.krino.voting_system.repository.CitizenRepository;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CitizenServiceOwnProfileTest
{
    private final Map<UUID, Citizen> citizens = new HashMap<>();
    private CitizenService citizenService;

    @BeforeEach
    void setUp()
    {
        citizens.clear();
        citizenService = new CitizenService(repositoryStub(), new CitizenMapper()
        {
            @Override
            public Citizen toEntity(CitizenSyncRequest request)
            {
                throw new UnsupportedOperationException("Not needed in this test");
            }

            @Override
            public void updateEntity(CitizenSyncRequest request, Citizen citizen)
            {
                throw new UnsupportedOperationException("Not needed in this test");
            }
        });
    }

    @Test
    void updateOwnProfileUpdatesOnlyEditableFields()
    {
        UUID keycloakId = UUID.randomUUID();
        citizens.put(keycloakId, Citizen.builder()
                .keycloakId(keycloakId)
                .username("4bderrahmane")
                .firstName("Abderrahmane")
                .lastName("Khbabez")
                .cin("AA1111")
                .email("4bderrahmane@example.com")
                .emailVerified(true)
                .phoneNumber("0600000000")
                .address("Old street")
                .region("Old region")
                .birthPlace("Old city")
                .birthDate(LocalDate.of(2005, 1, 24))
                .isDeleted(false)
                .build());

        CitizenSelfUpdateRequest request = new CitizenSelfUpdateRequest();
        request.setFirstName(" Riad ");
        request.setEmail("riad@example.com");
        request.setPhoneNumber("   ");
        request.setAddress("New street");
        request.setRegion("New region");
        request.setBirthPlace("New city");
        request.setBirthDate(LocalDate.of(1991, 2, 2));

        Citizen updated = citizenService.updateOwnProfile(keycloakId, request);

        assertEquals(keycloakId, updated.getKeycloakId());
        assertEquals("AA1111", updated.getCin());
        assertEquals("Riad", updated.getFirstName());
        assertEquals("riad@example.com", updated.getEmail());
        assertNull(updated.getPhoneNumber());
        assertEquals("New street", updated.getAddress());
        assertEquals("New region", updated.getRegion());
        assertEquals("New city", updated.getBirthPlace());
        assertEquals(LocalDate.of(1991, 2, 2), updated.getBirthDate());
        assertFalse(updated.isEmailVerified());
    }

    @Test
    void updateOwnProfileRejectsBlankFirstName()
    {
        UUID keycloakId = UUID.randomUUID();
        citizens.put(keycloakId, Citizen.builder()
                .keycloakId(keycloakId)
                .firstName("Abderrahmane")
                .lastName("Khbabez")
                .cin("AA11a1")
                .email("4bderrahmane@example.com")
                .isDeleted(false)
                .build());

        CitizenSelfUpdateRequest request = new CitizenSelfUpdateRequest();
        request.setFirstName("   ");

        assertThrows(IllegalArgumentException.class, () -> citizenService.updateOwnProfile(keycloakId, request));
    }

    private CitizenRepository repositoryStub()
    {
        return (CitizenRepository) Proxy.newProxyInstance(
                CitizenRepository.class.getClassLoader(),
                new Class[]{CitizenRepository.class},
                (proxy, method, args) ->
                {
                    return switch (method.getName())
                    {
                        case "findByKeycloakIdAndIsDeletedFalse" ->
                        {
                            UUID keycloakId = (UUID) args[0];
                            Citizen citizen = citizens.get(keycloakId);
                            yield citizen == null || citizen.isDeleted() ? Optional.empty() : Optional.of(citizen);
                        }
                        case "findByKeycloakId" -> Optional.ofNullable(citizens.get((UUID) args[0]));
                        case "save" ->
                        {
                            Citizen citizen = (Citizen) args[0];
                            citizens.put(citizen.getKeycloakId(), citizen);
                            yield citizen;
                        }
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "CitizenRepositoryStub";
                        default -> throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
                    };
                }
        );
    }
}
