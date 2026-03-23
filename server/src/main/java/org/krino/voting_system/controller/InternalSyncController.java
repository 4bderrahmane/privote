package org.krino.voting_system.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.krino.voting_system.dto.citizen.CitizenSyncRequest;
import org.krino.voting_system.security.SyncRequestAuthenticator;
import org.krino.voting_system.service.CitizenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalSyncController
{
    private final CitizenService citizenService;
    private final SyncRequestAuthenticator syncRequestAuthenticator;

    @PostMapping("/sync")
    public ResponseEntity<Void> sync(
            @RequestHeader(value = "X-Sync-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Sync-Signature", required = false) String signature,
            @Valid @RequestBody CitizenSyncRequest request
    )
    {
        syncRequestAuthenticator.verifySyncRequest(timestamp, signature, request);

        citizenService.sync(request);
        return ResponseEntity.noContent().build();
    }
}
