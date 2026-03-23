package org.privote.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.privote.backend.dto.party.PartyCreateDto;
import org.privote.backend.dto.party.PartyPatchDto;
import org.privote.backend.entity.Party;
import org.privote.backend.security.AuthenticatedActorResolver;
import org.privote.backend.service.PartyAdminService;
import org.privote.backend.service.PartyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/parties")
@RequiredArgsConstructor
public class PartyController
{
    private final PartyService partyService;
    private final PartyAdminService partyAdminService;
    private final AuthenticatedActorResolver authenticatedActorResolver;

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Party> createParty(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PartyCreateDto party)
    {
        UUID actorId = authenticatedActorResolver.actorId(jwt);
        log.debug("Admin request createParty actorId={}", actorId);
        Party createdParty = partyAdminService.createParty(actorId, party);
        return ResponseEntity.ok(createdParty);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Party> updateParty(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID uuid,
            @Valid @RequestBody PartyCreateDto party
    )
    {
        UUID actorId = authenticatedActorResolver.actorId(jwt);
        log.debug("Admin request updateParty actorId={} partyUuid={}", actorId, uuid);
        Party updatedParty = partyAdminService.updateParty(actorId, uuid, party);
        return ResponseEntity.ok(updatedParty);
    }

    @PatchMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Party> patchParty(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID uuid,
            @Valid @RequestBody PartyPatchDto party
    )
    {
        UUID actorId = authenticatedActorResolver.actorId(jwt);
        log.debug("Admin request patchParty actorId={} partyUuid={}", actorId, uuid);
        Party patchedParty = partyAdminService.patchParty(actorId, uuid, party);
        return ResponseEntity.ok(patchedParty);
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<Party> getPartyByUUID(@PathVariable UUID uuid)
    {
        Party party = partyService.getPartyByPublicId(uuid);
        return ResponseEntity.ok(party);
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletePartyByUUID(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID uuid)
    {
        UUID actorId = authenticatedActorResolver.actorId(jwt);
        log.debug("Admin request deleteParty actorId={} partyUuid={}", actorId, uuid);
        partyAdminService.deleteParty(actorId, uuid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<Party>> getAllParties()
    {
        List<Party> parties = partyService.findAllParties();
        return ResponseEntity.ok(parties);
    }
}
