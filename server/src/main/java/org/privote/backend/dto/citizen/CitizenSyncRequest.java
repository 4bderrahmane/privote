package org.privote.backend.dto.citizen;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CitizenSyncRequest(
        @NotNull(message = "keycloakId is required")
        UUID keycloakId,
        String username,
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        String email,
        @NotBlank(message = "firstName is required")
        String firstName,
        @NotBlank(message = "lastName is required")
        String lastName,
        @NotBlank(message = "cin is required")
        String cin,
        LocalDate birthDate,
        String birthPlace,
        String phoneNumber,
        boolean emailVerified,
        boolean enabled
)
{
}
