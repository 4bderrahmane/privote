package org.privote.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.privote.backend.dto.result.ElectionResultResponseDto;
import org.privote.backend.dto.result.PublishElectionResultsRequestDto;
import org.privote.backend.dto.result.TallyBallotResponseDto;
import org.privote.backend.security.AuthenticatedActorResolver;
import org.privote.backend.service.ElectionResultAdminService;
import org.privote.backend.service.ElectionResultService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/elections/{electionUuid}/results")
public class ElectionResultController
{
    private final ElectionResultService electionResultService;
    private final ElectionResultAdminService electionResultAdminService;
    private final AuthenticatedActorResolver authenticatedActorResolver;

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
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID electionUuid,
            @Valid @RequestBody PublishElectionResultsRequestDto request
    )
    {
        UUID actorId = authenticatedActorResolver.actorId(jwt);
        log.debug("Admin request publishResults actorId={} electionUuid={}", actorId, electionUuid);
        return ResponseEntity.ok(electionResultAdminService.publishResults(actorId, electionUuid, request));
    }
}
