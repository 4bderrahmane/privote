package org.krino.voting_system.controller;

import lombok.RequiredArgsConstructor;
import org.krino.voting_system.dto.result.ElectionResultResponseDto;
import org.krino.voting_system.dto.result.PublishElectionResultsRequestDto;
import org.krino.voting_system.dto.result.TallyBallotResponseDto;
import org.krino.voting_system.service.ElectionResultService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/elections/{electionUuid}/results")
public class ElectionResultController
{
    private final ElectionResultService electionResultService;

    @GetMapping
    public ResponseEntity<ElectionResultResponseDto> getElectionResults(@PathVariable UUID electionUuid)
    {
        return ResponseEntity.ok(electionResultService.getElectionResults(electionUuid));
    }

    @GetMapping("/ballots")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TallyBallotResponseDto>> getTalliableBallots(@PathVariable UUID electionUuid)
    {
        return ResponseEntity.ok(electionResultService.getTalliableBallots(electionUuid));
    }

    @PostMapping("/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ElectionResultResponseDto> publishResults(
            @PathVariable UUID electionUuid,
            @RequestBody PublishElectionResultsRequestDto request
    )
    {
        return ResponseEntity.ok(electionResultService.publishElectionResults(electionUuid, request));
    }
}
