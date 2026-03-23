package org.krino.voting_system.dto.party;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class PartyCreateDto
{
    @NotBlank(message = "name is required")
    private String name;

    private String abbreviation;

    private String description;

    @NotEmpty(message = "at least one member CIN is required")
    private List<@NotBlank(message = "member CIN values must not be blank") String> memberCins;
}
