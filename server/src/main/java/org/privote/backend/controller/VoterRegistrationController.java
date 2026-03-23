package org.privote.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.privote.backend.dto.election.VoterRegistrationRequestDto;
import org.privote.backend.dto.election.VoterRegistrationResponseDto;
import org.privote.backend.security.AuthenticatedActorResolver;
import org.privote.backend.service.VoterRegistrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/elections/{electionUuid}/registrations")
public class VoterRegistrationController
{
    private final VoterRegistrationService voterRegistrationService;
    private final AuthenticatedActorResolver authenticatedActorResolver;

    @PostMapping("/me")
    public ResponseEntity<VoterRegistrationResponseDto> registerMe(
            @PathVariable UUID electionUuid,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody VoterRegistrationRequestDto request
    )
    {
        return ResponseEntity.ok(voterRegistrationService.registerMyCommitment(
                electionUuid,
                authenticatedActorResolver.actorId(jwt),
                request
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<VoterRegistrationResponseDto> getMyRegistration(
            @PathVariable UUID electionUuid,
            @AuthenticationPrincipal Jwt jwt
    )
    {
        return ResponseEntity.ok(voterRegistrationService.getMyRegistration(
                electionUuid,
                authenticatedActorResolver.actorId(jwt)
        ));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<VoterRegistrationResponseDto>> getRegistrations(@PathVariable UUID electionUuid)
    {
        return ResponseEntity.ok(voterRegistrationService.getRegistrationsByElection(electionUuid));
    }
}
