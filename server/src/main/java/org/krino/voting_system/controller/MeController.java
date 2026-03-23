package org.krino.voting_system.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.krino.voting_system.dto.citizen.CitizenResponseDto;
import org.krino.voting_system.dto.citizen.CitizenSelfUpdateRequest;
import org.krino.voting_system.security.AuthenticatedActorResolver;
import org.krino.voting_system.service.account.AccountDeletionService;
import org.krino.voting_system.service.account.AccountProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class MeController
{

    private final AccountDeletionService accountDeletionService;
    private final AccountProfileService accountProfileService;
    private final AuthenticatedActorResolver authenticatedActorResolver;

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal Jwt jwt)
    {
        accountDeletionService.deleteMyAccount(authenticatedActorResolver.subject(jwt));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<CitizenResponseDto> me(@AuthenticationPrincipal Jwt jwt)
    {
        return ResponseEntity.ok(accountProfileService.getMyProfile(authenticatedActorResolver.subject(jwt)));
    }

    @PatchMapping("/me")
    public ResponseEntity<CitizenResponseDto> updateMe(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CitizenSelfUpdateRequest request
    )
    {
        return ResponseEntity.ok(accountProfileService.updateMyProfile(authenticatedActorResolver.subject(jwt), request));
    }

}
