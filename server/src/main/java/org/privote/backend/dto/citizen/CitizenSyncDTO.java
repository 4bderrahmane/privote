package org.privote.backend.dto.citizen;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CitizenSyncDTO
{
    @JsonProperty("sub")
    private UUID keycloakId;

    @JsonProperty("email")
    private String email;

    @JsonProperty("given_name")
    private String firstName;

    @JsonProperty("family_name")
    private String lastName;

    @JsonProperty("phone_number")
    private String phoneNumber;

    @JsonProperty("birthdate")
    private LocalDate birthDate;

    @JsonProperty("birthplace")
    private String birthPlace;

    @JsonProperty("cin")
    private String cin;
}