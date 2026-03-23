package org.privote.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.privote.backend.dto.citizen.CitizenSyncRequest;
import org.privote.backend.security.SyncRequestAuthenticator;
import org.privote.backend.service.CitizenService;
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
