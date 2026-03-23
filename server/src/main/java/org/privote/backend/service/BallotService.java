package org.privote.backend.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.privote.backend.dto.ballot.BallotCreateDto;
import org.privote.backend.entity.Ballot;
import org.privote.backend.entity.Candidate;
import org.privote.backend.entity.Election;
import org.privote.backend.exception.ResourceNotFoundException;
import org.privote.backend.mapper.BallotMapper;
import org.privote.backend.repository.BallotRepository;
import org.privote.backend.repository.CandidateRepository;
import org.privote.backend.repository.ElectionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Transactional
@Service
@RequiredArgsConstructor
public class BallotService
{
    private final BallotRepository ballotRepository;
    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final BallotMapper ballotMapper;

    public List<Ballot> getAllBallots()
    {
        return ballotRepository.findAll();
    }

    public Ballot getBallotById(UUID ballotId)
    {
        return ballotRepository.findById(ballotId)
                .orElseThrow(() -> new ResourceNotFoundException(Ballot.class.getSimpleName(), "id", ballotId));
    }

    public List<Ballot> getBallotsByElectionPublicId(UUID electionPublicId)
    {
        resolveElection(electionPublicId);
        return ballotRepository.findByElectionPublicId(electionPublicId);
    }

    public Ballot createBallot(BallotCreateDto ballotDto)
    {
        validateBallotDto(ballotDto);
        ensureBallotUniqueness(ballotDto, null);

        Ballot ballot = ballotMapper.toEntity(ballotDto);
        ballot.setElection(resolveElection(ballotDto.getElectionPublicId()));
        ballot.setCandidate(resolveCandidate(ballotDto.getCandidateId()));

        return ballotRepository.save(ballot);
    }

    public Ballot updateBallot(UUID ballotId, BallotCreateDto ballotDto)
    {
        validateBallotDto(ballotDto);

        Ballot ballot = getBallotById(ballotId);
        ensureBallotUniqueness(ballotDto, ballotId);

        ballotMapper.updateEntity(ballotDto, ballot);
        ballot.setElection(resolveElection(ballotDto.getElectionPublicId()));
        ballot.setCandidate(resolveCandidate(ballotDto.getCandidateId()));

        return ballotRepository.save(ballot);
    }

    public void deleteBallot(UUID ballotId)
    {
        Ballot ballot = getBallotById(ballotId);
        ballotRepository.delete(ballot);
    }

    private void validateBallotDto(BallotCreateDto ballotDto)
    {
        if (ballotDto == null)
        {
            throw new IllegalArgumentException("Ballot payload is required");
        }
        if (ballotDto.getElectionPublicId() == null)
        {
            throw new IllegalArgumentException("electionPublicId is required");
        }
        if (ballotDto.getCiphertext() == null || ballotDto.getCiphertext().length == 0)
        {
            throw new IllegalArgumentException("ciphertext is required");
        }
        if (ballotDto.getCiphertextHash() == null || ballotDto.getCiphertextHash().isBlank())
        {
            throw new IllegalArgumentException("ciphertextHash is required");
        }
        if (ballotDto.getNullifier() == null || ballotDto.getNullifier().isBlank())
        {
            throw new IllegalArgumentException("nullifier is required");
        }
        if (ballotDto.getTransactionHash() == null || ballotDto.getTransactionHash().isBlank())
        {
            throw new IllegalArgumentException("transactionHash is required");
        }
    }

    private void ensureBallotUniqueness(BallotCreateDto ballotDto, UUID currentBallotId)
    {
        ballotRepository.findByTransactionHash(ballotDto.getTransactionHash()).ifPresent(existing ->
        {
            if (currentBallotId == null || !existing.getId().equals(currentBallotId))
            {
                throw new IllegalStateException("transactionHash already used by another ballot");
            }
        });

        ballotRepository.findByElectionPublicIdAndNullifier(ballotDto.getElectionPublicId(), ballotDto.getNullifier())
                .ifPresent(existing ->
                {
                    if (currentBallotId == null || !existing.getId().equals(currentBallotId))
                    {
                        throw new IllegalStateException("nullifier already used in this election");
                    }
                });
    }

    private Election resolveElection(UUID electionPublicId)
    {
        return electionRepository.findByPublicId(electionPublicId)
                .orElseThrow(() -> new ResourceNotFoundException(Election.class.getSimpleName(), "UUID", electionPublicId));
    }

    private Candidate resolveCandidate(Long candidateId)
    {
        if (candidateId == null)
        {
            return null;
        }

        return candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException(Candidate.class.getSimpleName(), "id", candidateId));
    }
}
