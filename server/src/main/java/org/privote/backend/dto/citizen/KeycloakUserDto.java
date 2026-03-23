package org.privote.backend.dto.citizen;

import lombok.Data;

import java.util.UUID;

@Data
public class KeycloakUserDto
{
    private UUID publicId;

    private String firstName;

    private String lastName;

    private String email;

    private String cin;

    private String phoneNumber;

    private String birthPlace;


}
