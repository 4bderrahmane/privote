package org.krino.voting_system.dto.party;

import lombok.Data;

import java.util.List;

@Data
public class PartyCreateDto
{
    private String name;

    private String abbreviation;

    private String description;

    private List<String> memberCins;
}
