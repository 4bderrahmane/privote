package org.krino.voting_system.dto.result;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PublishElectionResultsRequestDto
{
    private List<TallyBallotAssignmentDto> assignments = new ArrayList<>();
}
