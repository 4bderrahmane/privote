package org.privote.backend.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.privote.backend.dto.result.ElectionResultCandidateDto;
import org.privote.backend.dto.result.ElectionResultResponseDto;
import org.privote.backend.dto.result.PublishElectionResultsRequestDto;
import org.privote.backend.dto.result.TallyBallotAssignmentDto;
import org.privote.backend.dto.result.TallyBallotResponseDto;
import org.privote.backend.entity.Ballot;
import org.privote.backend.entity.Candidate;
import org.privote.backend.entity.Election;
import org.privote.backend.entity.enums.ElectionPhase;
import org.privote.backend.exception.ResourceNotFoundException;
import org.privote.backend.repository.BallotRepository;
import org.privote.backend.repository.CandidateRepository;
import org.privote.backend.repository.CitizenElectionParticipationRepository;
import org.privote.backend.repository.ElectionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ElectionResultService
{
    private final ElectionRepository electionRepository;
    private final BallotRepository ballotRepository;
    private final CandidateRepository candidateRepository;
    private final CitizenElectionParticipationRepository participationRepository;

    public ElectionResultResponseDto getElectionResults(UUID electionPublicId)
    {
        Election election = requireElection(electionPublicId);
        requireTallyPhase(election);
        return toResultResponse(election, ballotRepository.findByElectionPublicId(electionPublicId));
    }

    public List<TallyBallotResponseDto> getTalliableBallots(UUID electionPublicId)
    {
        Election election = requireElection(electionPublicId);
        requireTallyPhase(election);

        return ballotRepository.findByElectionPublicId(electionPublicId)
                .stream()
                .map(this::toTallyBallotResponse)
                .toList();
    }

    public ElectionResultResponseDto publishElectionResults(UUID electionPublicId, PublishElectionResultsRequestDto request)
    {
        if (request == null)
        {
            throw new IllegalArgumentException("Results payload is required");
        }

        Election election = requireElection(electionPublicId);
        requireTallyPhase(election);

        List<Ballot> ballots = ballotRepository.findByElectionPublicId(electionPublicId);
        List<TallyBallotAssignmentDto> assignments = request.getAssignments() == null ? List.of() : request.getAssignments();

        if (ballots.isEmpty())
        {
            if (!assignments.isEmpty())
            {
                throw new IllegalArgumentException("No ballots exist for this election");
            }
            return toResultResponse(election, ballots);
        }

        if (assignments.size() != ballots.size())
        {
            throw new IllegalArgumentException("Results must resolve every ballot in the election");
        }

        Map<UUID, Ballot> ballotsById = new HashMap<>();
        for (Ballot ballot : ballots)
        {
            ballotsById.put(ballot.getId(), ballot);
        }

        Map<UUID, Candidate> candidatesByPublicId = candidateRepository.findByElectionPublicId(electionPublicId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(Candidate::getPublicId, candidate -> candidate));

        Set<UUID> seenBallotIds = new HashSet<>();
        for (TallyBallotAssignmentDto assignment : assignments)
        {
            if (assignment == null || assignment.getBallotId() == null || assignment.getCandidatePublicId() == null)
            {
                throw new IllegalArgumentException("Each tally assignment requires ballotId and candidatePublicId");
            }

            if (!seenBallotIds.add(assignment.getBallotId()))
            {
                throw new IllegalArgumentException("Duplicate ballotId found in results payload");
            }

            Ballot ballot = ballotsById.get(assignment.getBallotId());
            if (ballot == null)
            {
                throw new ResourceNotFoundException(Ballot.class.getSimpleName(), "id", assignment.getBallotId());
            }

            Candidate candidate = candidatesByPublicId.get(assignment.getCandidatePublicId());
            if (candidate == null)
            {
                throw new ResourceNotFoundException(Candidate.class.getSimpleName(), "publicId", assignment.getCandidatePublicId());
            }

            ballot.setCandidate(candidate);
        }

        if (seenBallotIds.size() != ballots.size())
        {
            throw new IllegalArgumentException("Results payload does not cover every ballot");
        }

        ballotRepository.saveAll(ballots);
        return toResultResponse(election, ballots);
    }

    private Election requireElection(UUID electionPublicId)
    {
        return electionRepository.findByPublicId(electionPublicId)
                .orElseThrow(() -> new ResourceNotFoundException(Election.class.getSimpleName(), "UUID", electionPublicId));
    }

    private static void requireTallyPhase(Election election)
    {
        if (election.getPhase() != ElectionPhase.TALLY)
        {
            throw new IllegalStateException("Results are only available once the election enters TALLY");
        }
    }

    private TallyBallotResponseDto toTallyBallotResponse(Ballot ballot)
    {
        String ciphertext = ballot.getCiphertext() == null ? null : Base64.getEncoder().encodeToString(ballot.getCiphertext());
        UUID candidatePublicId = ballot.getCandidate() == null ? null : ballot.getCandidate().getPublicId();
        return new TallyBallotResponseDto(
                ballot.getId(),
                ciphertext,
                ballot.getCiphertextHash(),
                ballot.getTransactionHash(),
                ballot.getBlockNumber(),
                ballot.getCastAt(),
                candidatePublicId
        );
    }

    private ElectionResultResponseDto toResultResponse(Election election, List<Ballot> ballots)
    {
        List<Candidate> candidates = candidateRepository.findByElectionPublicId(election.getPublicId());
        Map<UUID, Long> votesByCandidate = new HashMap<>();
        long talliedBallots = 0;

        for (Ballot ballot : ballots)
        {
            Candidate candidate = ballot.getCandidate();
            if (candidate == null || candidate.getPublicId() == null)
            {
                continue;
            }

            talliedBallots += 1;
            votesByCandidate.merge(candidate.getPublicId(), 1L, Long::sum);
        }

        long totalVotes = ballots.size();
        long registeredVoters = participationRepository.findByElectionPublicId(election.getPublicId()).size();
        boolean published = totalVotes == 0 || talliedBallots == totalVotes;

        List<ElectionResultCandidateDto> candidateResults = candidates.stream()
                .map(candidate ->
                {
                    long votes = votesByCandidate.getOrDefault(candidate.getPublicId(), 0L);
                    return new ElectionResultCandidateDto(
                            candidate.getPublicId(),
                            buildFullName(candidate),
                            candidate.getParty() == null ? null : candidate.getParty().getName(),
                            votes,
                            percentage(votes, totalVotes)
                    );
                })
                .sorted(Comparator
                        .comparingLong(ElectionResultCandidateDto::votes).reversed()
                        .thenComparing(result -> result.fullName() == null ? "" : result.fullName(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new ElectionResultResponseDto(
                election.getPublicId(),
                election.getTitle(),
                election.getEndTime(),
                published,
                totalVotes,
                talliedBallots,
                registeredVoters,
                percentage(totalVotes, registeredVoters),
                candidateResults
        );
    }

    private static String buildFullName(Candidate candidate)
    {
        return java.util.stream.Stream.of(
                        candidate.getCitizen() == null ? null : candidate.getCitizen().getFirstName(),
                        candidate.getCitizen() == null ? null : candidate.getCitizen().getLastName()
                )
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private static double percentage(long numerator, long denominator)
    {
        if (numerator <= 0 || denominator <= 0)
        {
            return 0.0d;
        }

        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
