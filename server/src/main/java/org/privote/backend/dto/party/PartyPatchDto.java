package org.privote.backend.dto.party;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PartyPatchDto
{
    @Pattern(regexp = ".*\\S.*", message = "name cannot be blank")
    private String name;
    private String description;
}
