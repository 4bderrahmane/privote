package org.krino.voting_system.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.krino.voting_system.dto.candidate.CandidateCreateByCinDto;
import org.krino.voting_system.dto.candidate.CandidateCreateDto;
import org.krino.voting_system.dto.candidate.CandidatePatchDto;
import org.krino.voting_system.dto.candidate.CandidateResponseDto;
import org.krino.voting_system.security.AuthenticatedActorResolver;
import org.krino.voting_system.service.CandidateAdminService;
import org.krino.voting_system.service.CandidateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/candidates")
@RequiredArgsConstructor
public class CandidateController
{
    private final CandidateService candidateService;
    private final CandidateAdminService candidateAdminService;
    private final AuthenticatedActorResolver authenticatedActorResolver;

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CandidateResponseDto> createCandidate(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CandidateCreateDto candidate
    )
    {
        UUID actorId = authenticatedActorResolver.actorId(jwt);
        log.debug("Admin request createCandidate actorId={}", actorId);
        CandidateResponseDto createdCandidate = candidateAdminService.createCandidate(actorId, candidate);
        return ResponseEntity.ok(createdCandidate);
    }

    @PostMapping("/election/{electionUuid}/by-cin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CandidateResponseDto> createCandidateByCin(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID electionUuid,
            @Valid @RequestBody CandidateCreateByCinDto candidate
    )
    {
        UUID actorId = authenticatedActorResolver.actorId(jwt);
        log.debug("Admin request createCandidateByCin actorId={} electionUuid={}", actorId, electionUuid);
        CandidateResponseDto createdCandidate = candidateAdminService.createCandidateByCin(actorId, electionUuid, candidate);
        return ResponseEntity.ok(createdCandidate);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CandidateResponseDto> updateCandidate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID uuid,
            @Valid @RequestBody CandidateCreateDto candidate
    )
    {
        UUID actorId = authenticatedActorResolver.actorId(jwt);
        log.debug("Admin request updateCandidate actorId={} candidateUuid={}", actorId, uuid);
        CandidateResponseDto updatedCandidate = candidateAdminService.updateCandidate(actorId, uuid, candidate);
        return ResponseEntity.ok(updatedCandidate);
    }

    @PatchMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CandidateResponseDto> patchCandidate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID uuid,
            @Valid @RequestBody CandidatePatchDto candidate
    )
    {
        UUID actorId = authenticatedActorResolver.actorId(jwt);
        log.debug("Admin request patchCandidate actorId={} candidateUuid={}", actorId, uuid);
        CandidateResponseDto patchedCandidate = candidateAdminService.patchCandidate(actorId, uuid, candidate);
        return ResponseEntity.ok(patchedCandidate);
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<CandidateResponseDto> getCandidateByUUID(@PathVariable UUID uuid)
    {
        CandidateResponseDto candidate = candidateService.getCandidateByPublicId(uuid);
        return ResponseEntity.ok(candidate);
    }

    @GetMapping("/election/{electionUuid}")
    public ResponseEntity<List<CandidateResponseDto>> getCandidatesByElectionUUID(@PathVariable UUID electionUuid)
    {
        List<CandidateResponseDto> candidates = candidateService.getCandidatesByElectionPublicId(electionUuid);
        return ResponseEntity.ok(candidates);
    }

    @GetMapping("/election/{electionUuid}/active")
    public ResponseEntity<List<CandidateResponseDto>> getActiveCandidatesByElectionUUID(@PathVariable UUID electionUuid)
    {
        List<CandidateResponseDto> candidates = candidateService.getActiveCandidatesByElectionPublicId(electionUuid);
        return ResponseEntity.ok(candidates);
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCandidateByUUID(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID uuid
    )
    {
        UUID actorId = authenticatedActorResolver.actorId(jwt);
        log.debug("Admin request deleteCandidate actorId={} candidateUuid={}", actorId, uuid);
        candidateAdminService.deleteCandidate(actorId, uuid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<CandidateResponseDto>> getAllCandidates()
    {
        List<CandidateResponseDto> candidates = candidateService.getAllCandidates();
        return ResponseEntity.ok(candidates);
    }
}
