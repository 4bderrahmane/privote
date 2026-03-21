package org.krino.voting_system.controller;

import lombok.RequiredArgsConstructor;
import org.krino.voting_system.dto.election.ElectionEndRequestDto;
import org.krino.voting_system.dto.election.ElectionCreateDto;
import org.krino.voting_system.dto.election.ElectionPatchDto;
import org.krino.voting_system.entity.Election;
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

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Election> createElection(@RequestBody ElectionCreateDto election)
    {
        Election createdElection = electionService.createElection(election);
        return ResponseEntity.ok(createdElection);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Election> updateElection(@PathVariable UUID uuid, @RequestBody ElectionCreateDto election)
    {
        Election updatedElection = electionService.updateElection(uuid, election);
        return ResponseEntity.ok(updatedElection);
    }

    @PatchMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Election> patchElection(@PathVariable UUID uuid, @RequestBody ElectionPatchDto patch)
    {
        Election patchedElection = electionService.patchElection(uuid, patch);
        return ResponseEntity.ok(patchedElection);
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<Election> getElectionByUUID(@PathVariable UUID uuid)
    {
        Election election = electionService.getElectionByPublicId(uuid);
        return ResponseEntity.ok(election);
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
    public ResponseEntity<Election> deployElection(@PathVariable UUID uuid)
    {
        return ResponseEntity.ok(electionLifecycleService.deployElection(uuid));
    }

    @PostMapping("/{uuid}/start")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Election> startElection(@PathVariable UUID uuid)
    {
        return ResponseEntity.ok(electionLifecycleService.startElection(uuid));
    }

    @PostMapping("/{uuid}/end")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Election> endElection(@PathVariable UUID uuid, @RequestBody ElectionEndRequestDto request)
    {
        if (request == null || request.getDecryptionMaterial() == null || request.getDecryptionMaterial().length == 0)
        {
            throw new IllegalArgumentException("decryptionMaterial is required");
        }

        return ResponseEntity.ok(electionLifecycleService.endElection(uuid, request.getDecryptionMaterial()));
    }


    @GetMapping
    public ResponseEntity<List<Election>> getAllElections()
    {
        List<Election> elections = electionService.findAllElections();
        return ResponseEntity.ok(elections);
    }

}
