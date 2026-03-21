package org.krino.voting_system.controller;

import lombok.RequiredArgsConstructor;
import org.krino.voting_system.entity.Citizen;
import org.krino.voting_system.service.CitizenService;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/citizens")
@RequiredArgsConstructor
public class CitizenController
{
    private final CitizenService citizenService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Citizen>> getAllCitizens()
    {
        List<Citizen> citizens = citizenService.getAllCitizens();
        return ResponseEntity.ok(citizens);
    }


    @GetMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Citizen> getCitizenByUUID(@PathVariable UUID uuid)
    {
        Citizen citizen = citizenService.getCitizenByUUID(uuid);
        return ResponseEntity.ok(citizen);
    }


//    @DeleteMapping("/me")
//    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal Jwt jwt) {
//        String userId = jwt.getSubject();     // comes from validated token, not user input
//        keycloak.realm(realm).users().delete(userId);
//        return ResponseEntity.noContent().build();
//    }

}
