package org.privote.backend.dto.election;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VoterRegistrationRequestDto
{
    @NotBlank(message = "identityCommitment is required")
    @Pattern(regexp = "^[1-9]\\d*$", message = "identityCommitment must be a positive decimal integer")
    private String identityCommitment;
}
