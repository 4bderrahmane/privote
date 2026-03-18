package org.krino.voting_system.dto.citizen;

import lombok.Data;
import org.krino.voting_system.entity.Citizen;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CitizenResponseDto
{
    private UUID keycloakId;
    private String username;
    private String firstName;
    private String lastName;
    private String cin;
    private String email;
    private String phoneNumber;
    private String address;
    private String region;
    private String birthPlace;
    private LocalDate birthDate;
    private boolean emailVerified;

    public static CitizenResponseDto fromEntity(Citizen citizen)
    {
        CitizenResponseDto dto = new CitizenResponseDto();
        dto.setKeycloakId(citizen.getKeycloakId());
        dto.setUsername(citizen.getUsername());
        dto.setFirstName(citizen.getFirstName());
        dto.setLastName(citizen.getLastName());
        dto.setCin(citizen.getCin());
        dto.setEmail(citizen.getEmail());
        dto.setPhoneNumber(citizen.getPhoneNumber());
        dto.setAddress(citizen.getAddress());
        dto.setRegion(citizen.getRegion());
        dto.setBirthPlace(citizen.getBirthPlace());
        dto.setBirthDate(citizen.getBirthDate());
        dto.setEmailVerified(citizen.isEmailVerified());
        return dto;
    }

}
