package org.krino.voting_system.controller;

import lombok.RequiredArgsConstructor;
import org.krino.voting_system.dto.citizen.CitizenSyncRequest;
import org.krino.voting_system.service.CitizenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalSyncController
{
    private final CitizenService citizenService;

    @Value("${sync.secret}")
    private String expectedSecret;

    @PostMapping("/sync")
    public ResponseEntity<Void> sync(@RequestHeader(value = "X-Sync-Secret", required = false) String secret, @RequestBody CitizenSyncRequest request)
    {
        if (expectedSecret == null || !expectedSecret.equals(secret))
        {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid sync secret");
        }

        citizenService.sync(request);
        return ResponseEntity.noContent().build();
    }
}
