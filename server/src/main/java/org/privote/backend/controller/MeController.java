package org.privote.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.privote.backend.dto.citizen.CitizenResponseDto;
import org.privote.backend.dto.citizen.CitizenSelfUpdateRequest;
import org.privote.backend.security.AuthenticatedActorResolver;
import org.privote.backend.service.account.AccountDeletionService;
import org.privote.backend.service.account.AccountProfileService;
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
