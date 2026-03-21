package org.krino.voting_system.controller;

import lombok.RequiredArgsConstructor;
import org.krino.voting_system.dto.party.PartyCreateDto;
import org.krino.voting_system.dto.party.PartyPatchDto;
import org.krino.voting_system.entity.Party;
import org.krino.voting_system.service.PartyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/parties")
@RequiredArgsConstructor
public class PartyController
{
    private final PartyService partyService;

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Party> createParty(@RequestBody PartyCreateDto party)
    {
        Party createdParty = partyService.createParty(party);
        return ResponseEntity.ok(createdParty);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Party> updateParty(@PathVariable UUID uuid, @RequestBody PartyCreateDto party)
    {
        Party updatedParty = partyService.updateParty(uuid, party);
        return ResponseEntity.ok(updatedParty);
    }

    @PatchMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Party> patchParty(@PathVariable UUID uuid, @RequestBody PartyPatchDto party)
    {
        Party patchedParty = partyService.patchParty(uuid, party);
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
    public ResponseEntity<Void> deletePartyByUUID(@PathVariable UUID uuid)
    {
        partyService.deletePartyByPublicId(uuid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<Party>> getAllParties()
    {
        List<Party> parties = partyService.findAllParties();
        return ResponseEntity.ok(parties);
    }
}
