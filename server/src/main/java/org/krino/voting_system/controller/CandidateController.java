package org.krino.voting_system.controller;

import lombok.RequiredArgsConstructor;
import org.krino.voting_system.dto.candidate.CandidateCreateDto;
import org.krino.voting_system.dto.candidate.CandidatePatchDto;
import org.krino.voting_system.entity.Candidate;
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
    public ResponseEntity<Candidate> createCandidate(@RequestBody CandidateCreateDto candidate)
    {
        Candidate createdCandidate = candidateService.createCandidate(candidate);
        return ResponseEntity.ok(createdCandidate);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Candidate> updateCandidate(@PathVariable UUID uuid, @RequestBody CandidateCreateDto candidate)
    {
        Candidate updatedCandidate = candidateService.updateCandidate(uuid, candidate);
        return ResponseEntity.ok(updatedCandidate);
    }

    @PatchMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Candidate> patchCandidate(@PathVariable UUID uuid, @RequestBody CandidatePatchDto candidate)
    {
        Candidate patchedCandidate = candidateService.patchCandidate(uuid, candidate);
        return ResponseEntity.ok(patchedCandidate);
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<Candidate> getCandidateByUUID(@PathVariable UUID uuid)
    {
        Candidate candidate = candidateService.getCandidateByPublicId(uuid);
        return ResponseEntity.ok(candidate);
    }

    @GetMapping("/election/{electionUuid}")
    public ResponseEntity<List<Candidate>> getCandidatesByElectionUUID(@PathVariable UUID electionUuid)
    {
        List<Candidate> candidates = candidateService.getCandidatesByElectionPublicId(electionUuid);
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
    public ResponseEntity<List<Candidate>> getAllCandidates()
    {
        List<Candidate> candidates = candidateService.getAllCandidates();
        return ResponseEntity.ok(candidates);
    }
}
