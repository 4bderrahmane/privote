package org.privote.backend.service;

import org.junit.jupiter.api.Test;
import org.privote.backend.dto.election.ElectionCreateDto;
import org.privote.backend.dto.election.ElectionPatchDto;
import org.privote.backend.entity.Citizen;
import org.privote.backend.entity.Election;
import org.privote.backend.entity.enums.ElectionPhase;
import org.privote.backend.exception.BusinessConflictException;
import org.privote.backend.exception.RequestValidationException;
import org.privote.backend.mapper.ElectionMapperImpl;
import org.privote.backend.repository.CitizenRepository;
import org.privote.backend.repository.ElectionRepository;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ElectionServiceExternalNullifierTest
{
    @Test
    void createElectionDerivesCanonicalExternalNullifierFromPublicId()
    {
        Map<UUID, Election> elections = new HashMap<>();
        Map<UUID, Citizen> citizens = new HashMap<>();
        Citizen coordinator = Citizen.builder().keycloakId(UUID.randomUUID()).build();
        citizens.put(coordinator.getKeycloakId(), coordinator);

        ElectionService electionService = new ElectionService(
                electionRepositoryStub(elections),
                new ElectionMapperImpl(),
                citizenRepositoryStub(citizens)
        );

        ElectionCreateDto dto = new ElectionCreateDto();
        dto.setTitle("Election");
        dto.setEndTime(Instant.now().plusSeconds(3600));
        dto.setCoordinatorKeycloakId(coordinator.getKeycloakId());
        dto.setEncryptionPublicKey(new byte[32]);

        Election created = electionService.createElection(dto);

        assertEquals(ElectionService.deriveExternalNullifier(created.getPublicId()), created.getExternalNullifier());
    }

    @Test
    void createElectionRejectsNonCanonicalExternalNullifier()
    {
        Map<UUID, Election> elections = new HashMap<>();
        Map<UUID, Citizen> citizens = new HashMap<>();
        Citizen coordinator = Citizen.builder().keycloakId(UUID.randomUUID()).build();
        citizens.put(coordinator.getKeycloakId(), coordinator);

        ElectionService electionService = new ElectionService(
                electionRepositoryStub(elections),
                new ElectionMapperImpl(),
                citizenRepositoryStub(citizens)
        );

        ElectionCreateDto dto = new ElectionCreateDto();
        dto.setTitle("Election");
        dto.setEndTime(Instant.now().plusSeconds(3600));
        dto.setCoordinatorKeycloakId(coordinator.getKeycloakId());
        dto.setEncryptionPublicKey(new byte[32]);
        dto.setExternalNullifier(java.math.BigInteger.TEN);

        RequestValidationException ex = assertThrows(RequestValidationException.class, () -> electionService.createElection(dto));

        assertEquals("externalNullifier must match the canonical UUID-derived election scope", ex.getMessage());
    }

    @Test
    void createElectionRejectsNonRegistrationPhase()
    {
        Map<UUID, Election> elections = new HashMap<>();
        Map<UUID, Citizen> citizens = new HashMap<>();
        Citizen coordinator = Citizen.builder().keycloakId(UUID.randomUUID()).build();
        citizens.put(coordinator.getKeycloakId(), coordinator);

        ElectionService electionService = new ElectionService(
                electionRepositoryStub(elections),
                new ElectionMapperImpl(),
                citizenRepositoryStub(citizens)
        );

        ElectionCreateDto dto = new ElectionCreateDto();
        dto.setTitle("Election");
        dto.setEndTime(Instant.now().plusSeconds(3600));
        dto.setCoordinatorKeycloakId(coordinator.getKeycloakId());
        dto.setEncryptionPublicKey(new byte[32]);
        dto.setPhase(ElectionPhase.VOTING);

        RequestValidationException ex = assertThrows(RequestValidationException.class, () -> electionService.createElection(dto));

        assertEquals("New elections must start in REGISTRATION", ex.getMessage());
    }

    @Test
    void patchElectionRejectsDirectPhaseTransition()
    {
        Map<UUID, Election> elections = new HashMap<>();
        Map<UUID, Citizen> citizens = new HashMap<>();
        Citizen coordinator = Citizen.builder().keycloakId(UUID.randomUUID()).build();
        citizens.put(coordinator.getKeycloakId(), coordinator);

        Election election = new Election();
        election.setPublicId(UUID.randomUUID());
        election.setTitle("Election");
        election.setCoordinator(coordinator);
        election.setEndTime(Instant.now().plusSeconds(3600));
        election.setPhase(ElectionPhase.REGISTRATION);
        election.setExternalNullifier(ElectionService.deriveExternalNullifier(election.getPublicId()));
        election.setEncryptionPublicKey(new byte[32]);
        elections.put(election.getPublicId(), election);

        ElectionService electionService = new ElectionService(
                electionRepositoryStub(elections),
                new ElectionMapperImpl(),
                citizenRepositoryStub(citizens)
        );

        ElectionPatchDto patchDto = new ElectionPatchDto();
        patchDto.setPhase(ElectionPhase.VOTING);

        BusinessConflictException ex = assertThrows(
                BusinessConflictException.class,
                () -> electionService.patchElection(election.getPublicId(), patchDto)
        );

        assertEquals("Election phase transitions must use the lifecycle actions", ex.getMessage());
    }

    @Test
    void updateElectionRejectsDirectPhaseTransition()
    {
        Map<UUID, Election> elections = new HashMap<>();
        Map<UUID, Citizen> citizens = new HashMap<>();
        Citizen coordinator = Citizen.builder().keycloakId(UUID.randomUUID()).build();
        citizens.put(coordinator.getKeycloakId(), coordinator);

        Election election = new Election();
        election.setPublicId(UUID.randomUUID());
        election.setTitle("Election");
        election.setCoordinator(coordinator);
        election.setEndTime(Instant.now().plusSeconds(3600));
        election.setPhase(ElectionPhase.REGISTRATION);
        election.setExternalNullifier(ElectionService.deriveExternalNullifier(election.getPublicId()));
        election.setEncryptionPublicKey(new byte[32]);
        elections.put(election.getPublicId(), election);

        ElectionService electionService = new ElectionService(
                electionRepositoryStub(elections),
                new ElectionMapperImpl(),
                citizenRepositoryStub(citizens)
        );

        ElectionCreateDto updateDto = new ElectionCreateDto();
        updateDto.setTitle("Election");
        updateDto.setEndTime(Instant.now().plusSeconds(7200));
        updateDto.setCoordinatorKeycloakId(coordinator.getKeycloakId());
        updateDto.setEncryptionPublicKey(new byte[32]);
        updateDto.setPhase(ElectionPhase.VOTING);

        BusinessConflictException ex = assertThrows(
                BusinessConflictException.class,
                () -> electionService.updateElection(election.getPublicId(), updateDto)
        );

        assertEquals("Election phase transitions must use the lifecycle actions", ex.getMessage());
    }

    private ElectionRepository electionRepositoryStub(Map<UUID, Election> elections)
    {
        return (ElectionRepository) Proxy.newProxyInstance(
                ElectionRepository.class.getClassLoader(),
                new Class[]{ElectionRepository.class},
                (proxy, method, args) -> switch (method.getName())
                {
                    case "findByPublicId" -> Optional.ofNullable(elections.get((UUID) args[0]));
                    case "save" ->
                    {
                        Election election = (Election) args[0];
                        elections.put(election.getPublicId(), election);
                        yield election;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "ElectionRepositoryStub";
                    default ->
                            throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
                }
        );
    }

    private CitizenRepository citizenRepositoryStub(Map<UUID, Citizen> citizens)
    {
        return (CitizenRepository) Proxy.newProxyInstance(
                CitizenRepository.class.getClassLoader(),
                new Class[]{CitizenRepository.class},
                (proxy, method, args) -> switch (method.getName())
                {
                    case "findByKeycloakId" -> Optional.ofNullable(citizens.get((UUID) args[0]));
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "CitizenRepositoryStub";
                    default ->
                            throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
                }
        );
    }
}
