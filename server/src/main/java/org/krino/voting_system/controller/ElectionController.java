package org.krino.voting_system.controller;

import lombok.RequiredArgsConstructor;
import org.krino.voting_system.dto.candidate.CandidateCreateByCinDto;
import org.krino.voting_system.dto.candidate.CandidateResponseDto;
import org.krino.voting_system.dto.election.ElectionEndRequestDto;
import org.krino.voting_system.dto.election.ElectionCreateDto;
import org.krino.voting_system.dto.election.ElectionPatchDto;
import org.krino.voting_system.dto.election.ElectionResponseDto;
import org.krino.voting_system.entity.Election;
import org.krino.voting_system.service.CandidateService;
import org.krino.voting_system.service.ElectionLifecycleService;
import org.krino.voting_system.service.ElectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/elections")
@RequiredArgsConstructor
public class ElectionController
{
    private final ElectionService electionService;
    private final ElectionLifecycleService electionLifecycleService;
    private final CandidateService candidateService;

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionResponseDto> createElection(@RequestBody ElectionCreateDto election)
    {
        Election createdElection = electionService.createElection(election);
        return ResponseEntity.ok(ElectionResponseDto.fromEntity(createdElection));
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionResponseDto> updateElection(@PathVariable UUID uuid, @RequestBody ElectionCreateDto election)
    {
        Election updatedElection = electionService.updateElection(uuid, election);
        return ResponseEntity.ok(ElectionResponseDto.fromEntity(updatedElection));
    }

    @PatchMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionResponseDto> patchElection(@PathVariable UUID uuid, @RequestBody ElectionPatchDto patch)
    {
        Election patchedElection = electionService.patchElection(uuid, patch);
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
    public ResponseEntity<Void> deleteElectionByUUID(@PathVariable UUID uuid)
    {
        electionService.deleteElectionByPublicId(uuid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{uuid}/deploy")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionResponseDto> deployElection(@PathVariable UUID uuid)
    {
        return ResponseEntity.ok(ElectionResponseDto.fromEntity(electionLifecycleService.deployElection(uuid)));
    }

    @PostMapping("/{uuid}/start")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionResponseDto> startElection(@PathVariable UUID uuid)
    {
        return ResponseEntity.ok(ElectionResponseDto.fromEntity(electionLifecycleService.startElection(uuid)));
    }

    @PostMapping("/{uuid}/end")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionResponseDto> endElection(@PathVariable UUID uuid, @RequestBody ElectionEndRequestDto request)
    {
        if (request == null || request.getDecryptionMaterial() == null || request.getDecryptionMaterial().length == 0)
        {
            throw new IllegalArgumentException("decryptionMaterial is required");
        }

        return ResponseEntity.ok(ElectionResponseDto.fromEntity(
                electionLifecycleService.endElection(uuid, request.getDecryptionMaterial())
        ));
    }

    @PostMapping("/{uuid}/candidates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CandidateResponseDto> createCandidateForElection(
            @PathVariable UUID uuid,
            @RequestBody CandidateCreateByCinDto request
    )
    {
        return ResponseEntity.ok(candidateService.createCandidateByCin(uuid, request));
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
