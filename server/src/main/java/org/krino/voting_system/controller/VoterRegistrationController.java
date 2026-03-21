package org.krino.voting_system.controller;

import lombok.RequiredArgsConstructor;
import org.krino.voting_system.dto.election.VoterRegistrationRequestDto;
import org.krino.voting_system.dto.election.VoterRegistrationResponseDto;
import org.krino.voting_system.service.VoterRegistrationService;
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

    @PostMapping("/me")
    public ResponseEntity<VoterRegistrationResponseDto> registerMe(
            @PathVariable UUID electionUuid,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody VoterRegistrationRequestDto request
    )
    {
        return ResponseEntity.ok(voterRegistrationService.registerMyCommitment(
                electionUuid,
                UUID.fromString(jwt.getSubject()),
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
                UUID.fromString(jwt.getSubject())
        ));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<VoterRegistrationResponseDto>> getRegistrations(@PathVariable UUID electionUuid)
    {
        return ResponseEntity.ok(voterRegistrationService.getRegistrationsByElection(electionUuid));
    }
}
