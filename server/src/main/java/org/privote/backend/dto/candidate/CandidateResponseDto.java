package org.privote.backend.dto.candidate;

import org.privote.backend.entity.Candidate;
import org.privote.backend.entity.Party;
import org.privote.backend.entity.enums.CandidateStatus;

import java.util.Objects;
import java.util.UUID;

public record CandidateResponseDto(
        UUID publicId,
        UUID electionPublicId,
        CandidateStatus status,
        String fullName,
        UUID partyPublicId,
        String partyName
)
{
    public static CandidateResponseDto fromEntity(Candidate candidate)
    {
        Party party = candidate.getParty();
        return new CandidateResponseDto(
                candidate.getPublicId(),
                candidate.getElection().getPublicId(),
                candidate.getStatus(),
                buildFullName(candidate),
                party == null ? null : party.getPublicId(),
                party == null ? null : party.getName()
        );
    }

    private static String buildFullName(Candidate candidate)
    {
        return java.util.stream.Stream.of(
                        candidate.getCitizen().getFirstName(),
                        candidate.getCitizen().getLastName()
                )
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }
}
