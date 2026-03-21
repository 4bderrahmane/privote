package org.krino.voting_system.controller;

import lombok.RequiredArgsConstructor;
import org.krino.voting_system.dto.candidate.CandidateCreateByCinDto;
import org.krino.voting_system.dto.candidate.CandidateCreateDto;
import org.krino.voting_system.dto.candidate.CandidatePatchDto;
import org.krino.voting_system.dto.candidate.CandidateResponseDto;
import org.krino.voting_system.service.CandidateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/candidates")
@RequiredArgsConstructor
public class CandidateController
{
    private final CandidateService candidateService;

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CandidateResponseDto> createCandidate(@RequestBody CandidateCreateDto candidate)
    {
        CandidateResponseDto createdCandidate = candidateService.createCandidate(candidate);
        return ResponseEntity.ok(createdCandidate);
    }

    @PostMapping("/election/{electionUuid}/by-cin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CandidateResponseDto> createCandidateByCin(
            @PathVariable UUID electionUuid,
            @RequestBody CandidateCreateByCinDto candidate
    )
    {
        return ResponseEntity.ok(candidateService.createCandidateByCin(electionUuid, candidate));
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CandidateResponseDto> updateCandidate(@PathVariable UUID uuid, @RequestBody CandidateCreateDto candidate)
    {
        CandidateResponseDto updatedCandidate = candidateService.updateCandidate(uuid, candidate);
        return ResponseEntity.ok(updatedCandidate);
    }

    @PatchMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CandidateResponseDto> patchCandidate(@PathVariable UUID uuid, @RequestBody CandidatePatchDto candidate)
    {
        CandidateResponseDto patchedCandidate = candidateService.patchCandidate(uuid, candidate);
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
    public ResponseEntity<Void> deleteCandidateByUUID(@PathVariable UUID uuid)
    {
        candidateService.deleteCandidateByPublicId(uuid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<CandidateResponseDto>> getAllCandidates()
    {
        List<CandidateResponseDto> candidates = candidateService.getAllCandidates();
        return ResponseEntity.ok(candidates);
    }
}
