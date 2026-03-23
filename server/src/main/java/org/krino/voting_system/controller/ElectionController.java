package org.krino.voting_system.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.krino.voting_system.dto.candidate.CandidateCreateByCinDto;
import org.krino.voting_system.dto.candidate.CandidateResponseDto;
import org.krino.voting_system.dto.election.ElectionEndRequestDto;
import org.krino.voting_system.dto.election.ElectionCreateDto;
import org.krino.voting_system.dto.election.ElectionPatchDto;
import org.krino.voting_system.dto.election.ElectionResponseDto;
import org.krino.voting_system.entity.Election;
import org.krino.voting_system.security.AuthenticatedActorResolver;
import org.krino.voting_system.service.CandidateAdminService;
import org.krino.voting_system.service.CandidateService;
import org.krino.voting_system.service.ElectionAdminService;
import org.krino.voting_system.service.ElectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/elections")
@RequiredArgsConstructor
public class ElectionController
{
    private final ElectionService electionService;
    private final ElectionAdminService electionAdminService;
    private final CandidateService candidateService;
    private final CandidateAdminService candidateAdminService;
    private final AuthenticatedActorResolver authenticatedActorResolver;

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionResponseDto> createElection(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ElectionCreateDto election
    )
    {
        UUID actorId = authenticatedActorResolver.actorId(jwt);
        log.debug("Admin request createElection actorId={}", actorId);
        Election createdElection = electionAdminService.createElection(actorId, election);
        return ResponseEntity.ok(ElectionResponseDto.fromEntity(createdElection));
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionResponseDto> updateElection(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID uuid,
            @Valid @RequestBody ElectionCreateDto election
    )
    {
        UUID actorId = authenticatedActorResolver.actorId(jwt);
        log.debug("Admin request updateElection actorId={} electionUuid={}", actorId, uuid);
        Election updatedElection = electionAdminService.updateElection(actorId, uuid, election);
        return ResponseEntity.ok(ElectionResponseDto.fromEntity(updatedElection));
    }

    @PatchMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionResponseDto> patchElection(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID uuid,
            @Valid @RequestBody ElectionPatchDto patch
    )
    {
        UUID actorId = authenticatedActorResolver.actorId(jwt);
        log.debug("Admin request patchElection actorId={} electionUuid={}", actorId, uuid);
        Election patchedElection = electionAdminService.patchElection(actorId, uuid, patch);
        return ResponseEntity.ok(ElectionResponseDto.fromEntity(patchedElection));
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<ElectionResponseDto> getElectionByUUID(@PathVariable UUID uuid)
    {
        Election election = electionService.getElectionByPublicId(uuid);
        return ResponseEntity.ok(ElectionResponseDto.fromEntity(election));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteElectionByUUID(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID uuid
    )
    {
        UUID actorId = authenticatedActorResolver.actorId(jwt);
        log.debug("Admin request deleteElection actorId={} electionUuid={}", actorId, uuid);
        electionAdminService.deleteElection(actorId, uuid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{uuid}/deploy")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionResponseDto> deployElection(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID uuid
    )
    {
        UUID actorId = authenticatedActorResolver.actorId(jwt);
        log.debug("Admin request deployElection actorId={} electionUuid={}", actorId, uuid);
        Election deployedElection = electionAdminService.deployElection(actorId, uuid);
        return ResponseEntity.ok(ElectionResponseDto.fromEntity(deployedElection));
    }

    @PostMapping("/{uuid}/start")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionResponseDto> startElection(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID uuid
    )
    {
        UUID actorId = authenticatedActorResolver.actorId(jwt);
        log.debug("Admin request startElection actorId={} electionUuid={}", actorId, uuid);
        Election startedElection = electionAdminService.startElection(actorId, uuid);
        return ResponseEntity.ok(ElectionResponseDto.fromEntity(startedElection));
    }

    @PostMapping("/{uuid}/end")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionResponseDto> endElection(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID uuid,
            @Valid @RequestBody ElectionEndRequestDto request
    )
    {
        UUID actorId = authenticatedActorResolver.actorId(jwt);
        log.debug("Admin request endElection actorId={} electionUuid={}", actorId, uuid);
        Election endedElection = electionAdminService.endElection(actorId, uuid, request);
        return ResponseEntity.ok(ElectionResponseDto.fromEntity(endedElection));
    }

    @PostMapping("/{uuid}/candidates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CandidateResponseDto> createCandidateForElection(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID uuid,
            @Valid @RequestBody CandidateCreateByCinDto request
    )
    {
        UUID actorId = authenticatedActorResolver.actorId(jwt);
        log.debug("Admin request createCandidateForElection actorId={} electionUuid={}", actorId, uuid);
        CandidateResponseDto candidate = candidateAdminService.createCandidateByCin(actorId, uuid, request);
        return ResponseEntity.ok(candidate);
    }

    @GetMapping("/{uuid}/candidates")
    public ResponseEntity<List<CandidateResponseDto>> getCandidatesForElection(@PathVariable UUID uuid)
    {
        return ResponseEntity.ok(candidateService.getCandidatesByElectionPublicId(uuid));
    }

    @GetMapping("/{uuid}/candidates/active")
    public ResponseEntity<List<CandidateResponseDto>> getActiveCandidatesForElection(@PathVariable UUID uuid)
    {
        return ResponseEntity.ok(candidateService.getActiveCandidatesByElectionPublicId(uuid));
    }


    @GetMapping
    public ResponseEntity<List<ElectionResponseDto>> getAllElections()
    {
        List<Election> elections = electionService.findAllElections();
        return ResponseEntity.ok(elections.stream().map(ElectionResponseDto::fromEntity).toList());
    }
}
