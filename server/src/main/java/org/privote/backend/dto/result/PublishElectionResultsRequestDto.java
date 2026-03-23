package org.privote.backend.dto.result;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PublishElectionResultsRequestDto
{
    @NotNull(message = "assignments is required")
    private List<@Valid TallyBallotAssignmentDto> assignments = new ArrayList<>();
}
