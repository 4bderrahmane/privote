package org.krino.voting_system.service;

import org.junit.jupiter.api.Test;
import org.krino.voting_system.dto.candidate.CandidateCreateByCinDto;
import org.krino.voting_system.dto.candidate.CandidateResponseDto;
import org.krino.voting_system.entity.Candidate;
import org.krino.voting_system.entity.Citizen;
import org.krino.voting_system.entity.Election;
import org.krino.voting_system.entity.Party;
import org.krino.voting_system.entity.enums.CandidateStatus;
import org.krino.voting_system.entity.enums.ElectionPhase;
import org.krino.voting_system.mapper.CandidateMapperImpl;
import org.krino.voting_system.repository.CandidateRepository;
import org.krino.voting_system.repository.CitizenRepository;
import org.krino.voting_system.repository.ElectionRepository;
import org.krino.voting_system.repository.PartyRepository;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CandidateServiceTest
{
    @Test
    void createCandidateByCinAssignsCitizenToElectionAndReturnsSafeView()
    {
        Fixture fixture = new Fixture();
        Citizen citizen = fixture.citizen("AB123", "Alice", "Bennani");
        Election election = fixture.election(ElectionPhase.REGISTRATION);
        Party party = fixture.party("Progress", citizen);

        CandidateService candidateService = fixture.candidateService();

        CandidateCreateByCinDto dto = new CandidateCreateByCinDto();
        dto.setCitizenCin(citizen.getCin());
        dto.setPartyPublicId(party.getPublicId());
        dto.setStatus(CandidateStatus.ACTIVE);

        CandidateResponseDto created = candidateService.createCandidateByCin(election.getPublicId(), dto);

        assertEquals(election.getPublicId(), created.electionPublicId());
        assertEquals(CandidateStatus.ACTIVE, created.status());
        assertEquals("Alice Bennani", created.fullName());
        assertEquals(party.getPublicId(), created.partyPublicId());
        assertEquals("Progress", created.partyName());
    }

    @Test
    void createCandidateByCinRejectsCitizenOutsideSelectedParty()
    {
        Fixture fixture = new Fixture();
        Citizen citizen = fixture.citizen("AB123", "Alice", "Bennani");
        Election election = fixture.election(ElectionPhase.REGISTRATION);
        Party party = fixture.party("Progress");

        CandidateService candidateService = fixture.candidateService();

        CandidateCreateByCinDto dto = new CandidateCreateByCinDto();
        dto.setCitizenCin(citizen.getCin());
        dto.setPartyPublicId(party.getPublicId());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> candidateService.createCandidateByCin(election.getPublicId(), dto)
        );

        assertEquals("Citizen must be a member of the selected party", ex.getMessage());
    }

    @Test
    void createCandidateByCinRejectsElectionOutsideRegistration()
    {
        Fixture fixture = new Fixture();
        Citizen citizen = fixture.citizen("AB123", "Alice", "Bennani");
        Election election = fixture.election(ElectionPhase.VOTING);

        CandidateService candidateService = fixture.candidateService();

        CandidateCreateByCinDto dto = new CandidateCreateByCinDto();
        dto.setCitizenCin(citizen.getCin());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> candidateService.createCandidateByCin(election.getPublicId(), dto)
        );

        assertEquals("Candidates can only be managed while the election is in REGISTRATION", ex.getMessage());
    }

    @Test
    void getActiveCandidatesByElectionReturnsOnlyActiveEntries()
    {
        Fixture fixture = new Fixture();
        Citizen activeCitizen = fixture.citizen("AB123", "Alice", "Bennani");
        Citizen pendingCitizen = fixture.citizen("CD456", "Youssef", "Alaoui");
        Election election = fixture.election(ElectionPhase.REGISTRATION);

        fixture.savedCandidate(election, activeCitizen, null, CandidateStatus.ACTIVE);
        fixture.savedCandidate(election, pendingCitizen, null, CandidateStatus.PENDING_APPROVAL);

        CandidateService candidateService = fixture.candidateService();

        List<CandidateResponseDto> activeCandidates = candidateService.getActiveCandidatesByElectionPublicId(election.getPublicId());

        assertEquals(1, activeCandidates.size());
        assertEquals("Alice Bennani", activeCandidates.get(0).fullName());
        assertNull(activeCandidates.get(0).partyName());
    }

    private static final class Fixture
    {
        private final Map<UUID, Candidate> candidates = new HashMap<>();
        private final Map<UUID, Citizen> citizensByKeycloakId = new HashMap<>();
        private final Map<String, Citizen> citizensByCin = new HashMap<>();
        private final Map<UUID, Election> elections = new HashMap<>();
        private final Map<UUID, Party> parties = new HashMap<>();

        private CandidateService candidateService()
        {
            return new CandidateService(
                    candidateRepositoryStub(),
                    citizenRepositoryStub(),
                    electionRepositoryStub(),
                    partyRepositoryStub(),
                    new CandidateMapperImpl()
            );
        }

        private Citizen citizen(String cin, String firstName, String lastName)
        {
            Citizen citizen = Citizen.builder()
                    .keycloakId(UUID.randomUUID())
                    .cin(cin)
                    .firstName(firstName)
                    .lastName(lastName)
                    .email(firstName.toLowerCase() + "@example.com")
                    .build();
            citizensByKeycloakId.put(citizen.getKeycloakId(), citizen);
            citizensByCin.put(cin, citizen);
            return citizen;
        }

        private Election election(ElectionPhase phase)
        {
            Election election = new Election();
            election.setPublicId(UUID.randomUUID());
            election.setPhase(phase);
            election.setTitle("Election");
            election.setEndTime(Instant.now().plusSeconds(3600));
            election.setCoordinator(Citizen.builder().keycloakId(UUID.randomUUID()).build());
            election.setEncryptionPublicKey(new byte[32]);
            elections.put(election.getPublicId(), election);
            return election;
        }

        private Party party(String name, Citizen... members)
        {
            Party party = new Party();
            party.setPublicId(UUID.randomUUID());
            party.setName(name);
            party.setMembers(new ArrayList<>(List.of(members)));
            parties.put(party.getPublicId(), party);
            return party;
        }

        private Candidate savedCandidate(Election election, Citizen citizen, Party party, CandidateStatus status)
        {
            Candidate candidate = new Candidate();
            candidate.setId((long) (candidates.size() + 1));
            candidate.setPublicId(UUID.randomUUID());
            candidate.setElection(election);
            candidate.setCitizen(citizen);
            candidate.setParty(party);
            candidate.setStatus(status);
            candidates.put(candidate.getPublicId(), candidate);
            return candidate;
        }

        private CandidateRepository candidateRepositoryStub()
        {
            return (CandidateRepository) Proxy.newProxyInstance(
                    CandidateRepository.class.getClassLoader(),
                    new Class[]{CandidateRepository.class},
                    (proxy, method, args) -> switch (method.getName())
                    {
                        case "findAll" -> new ArrayList<>(candidates.values());
                        case "findByPublicId" -> Optional.ofNullable(candidates.get((UUID) args[0]));
                        case "findByElectionPublicId" -> candidates.values().stream()
                                .filter(candidate -> candidate.getElection().getPublicId().equals(args[0]))
                                .toList();
                        case "findByElectionPublicIdAndStatus" -> candidates.values().stream()
                                .filter(candidate -> candidate.getElection().getPublicId().equals(args[0]))
                                .filter(candidate -> candidate.getStatus() == args[1])
                                .toList();
                        case "existsByElectionPublicIdAndStatus" -> candidates.values().stream()
                                .anyMatch(candidate -> candidate.getElection().getPublicId().equals(args[0])
                                        && candidate.getStatus() == args[1]);
                        case "existsByElectionPublicIdAndCitizenKeycloakId" -> candidates.values().stream()
                                .anyMatch(candidate -> candidate.getElection().getPublicId().equals(args[0])
                                        && candidate.getCitizen().getKeycloakId().equals(args[1]));
                        case "existsByElectionPublicIdAndCitizenKeycloakIdAndPublicIdNot" -> candidates.values().stream()
                                .anyMatch(candidate -> candidate.getElection().getPublicId().equals(args[0])
                                        && candidate.getCitizen().getKeycloakId().equals(args[1])
                                        && !candidate.getPublicId().equals(args[2]));
                        case "save" ->
                        {
                            Candidate candidate = (Candidate) args[0];
                            if (candidate.getPublicId() == null)
                            {
                                candidate.setPublicId(UUID.randomUUID());
                            }
                            if (candidate.getId() == null)
                            {
                                candidate.setId((long) (candidates.size() + 1));
                            }
                            candidates.put(candidate.getPublicId(), candidate);
                            yield candidate;
                        }
                        case "delete" ->
                        {
                            Candidate candidate = (Candidate) args[0];
                            candidates.remove(candidate.getPublicId());
                            yield null;
                        }
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "CandidateRepositoryStub";
                        default -> throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
                    }
            );
        }

        private CitizenRepository citizenRepositoryStub()
        {
            return (CitizenRepository) Proxy.newProxyInstance(
                    CitizenRepository.class.getClassLoader(),
                    new Class[]{CitizenRepository.class},
                    (proxy, method, args) -> switch (method.getName())
                    {
                        case "findByKeycloakIdAndIsDeletedFalse" -> Optional.ofNullable(citizensByKeycloakId.get((UUID) args[0]));
                        case "findByCinAndIsDeletedFalse" -> Optional.ofNullable(citizensByCin.get((String) args[0]));
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "CitizenRepositoryStub";
                        default -> throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
                    }
            );
        }

        private ElectionRepository electionRepositoryStub()
        {
            return (ElectionRepository) Proxy.newProxyInstance(
                    ElectionRepository.class.getClassLoader(),
                    new Class[]{ElectionRepository.class},
                    (proxy, method, args) -> switch (method.getName())
                    {
                        case "findByPublicId" -> Optional.ofNullable(elections.get((UUID) args[0]));
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "ElectionRepositoryStub";
                        default -> throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
                    }
            );
        }

        private PartyRepository partyRepositoryStub()
        {
            return (PartyRepository) Proxy.newProxyInstance(
                    PartyRepository.class.getClassLoader(),
                    new Class[]{PartyRepository.class},
                    (proxy, method, args) -> switch (method.getName())
                    {
                        case "findByPublicId" -> Optional.ofNullable(parties.get((UUID) args[0]));
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "PartyRepositoryStub";
                        default -> throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
                    }
            );
        }
    }
}
