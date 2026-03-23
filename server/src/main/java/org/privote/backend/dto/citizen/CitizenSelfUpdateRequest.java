package org.privote.backend.dto.citizen;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CitizenSelfUpdateRequest
{
    @Pattern(regexp = ".*\\S.*", message = "firstName must not be blank")
    private String firstName;

    @Pattern(regexp = ".*\\S.*", message = "lastName must not be blank")
    private String lastName;

    @Email(message = "email must be a valid email address")
    private String email;
    private String phoneNumber;
    private String address;
    private String region;
    private String birthPlace;

    @Past(message = "birthDate must be in the past")
    private LocalDate birthDate;
}
