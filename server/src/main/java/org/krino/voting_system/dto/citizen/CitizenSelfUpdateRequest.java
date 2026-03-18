package org.krino.voting_system.dto.citizen;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CitizenSelfUpdateRequest
{
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String address;
    private String region;
    private String birthPlace;
    private LocalDate birthDate;
}
