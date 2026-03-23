package org.krino.voting_system.dto.ballot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BallotCastRequestDto
{
    @NotNull(message = "ciphertext is required")
    @Size(min = 1, message = "ciphertext is required")
    private byte[] ciphertext;

    @NotBlank(message = "nullifier is required")
    @Pattern(regexp = "^[1-9]\\d*$", message = "nullifier must be a positive decimal integer")
    private String nullifier;

    @NotNull(message = "proof is required")
    @Size(min = 8, max = 8, message = "proof must contain exactly 8 field elements")
    private List<
            @NotBlank(message = "proof elements are required")
            @Pattern(regexp = "^[1-9]\\d*$", message = "proof elements must be positive decimal integers")
            String> proof;
}
