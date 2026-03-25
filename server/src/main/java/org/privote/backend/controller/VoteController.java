package org.privote.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.privote.backend.dto.ballot.BallotCastRequestDto;
import org.privote.backend.dto.ballot.BallotCastResponseDto;
import org.privote.backend.security.AuthenticatedActorResolver;
import org.privote.backend.service.VoteService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/elections/{electionUuid}/votes")
public class VoteController
{
    private final VoteService voteService;
    private final AuthenticatedActorResolver authenticatedActorResolver;

    @PostMapping("/me")
    public ResponseEntity<BallotCastResponseDto> castMyVote(
            @PathVariable UUID electionUuid,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BallotCastRequestDto request
    )
    {
        return ResponseEntity.ok(voteService.castMyVote(
                electionUuid,
                authenticatedActorResolver.actorId(jwt),
                request
        ));
    }
}
