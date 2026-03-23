package org.privote.backend.web3.listener;

import org.privote.backend.web3.contracts.Election;
import org.privote.backend.web3.listener.events.ElectionEndedEvent;
import org.privote.backend.web3.listener.events.ElectionStartedEvent;
import org.privote.backend.web3.listener.events.MemberAddedEvent;
import org.privote.backend.web3.listener.events.VoteAddedEvent;
import org.web3j.abi.EventEncoder;
import org.web3j.protocol.core.methods.response.Log;

import java.math.BigInteger;
import java.util.Objects;

public final class ElectionLogDecoder
{
    public static final String ELECTION_STARTED_TOPIC0 = EventEncoder.encode(Election.ELECTIONSTARTED_EVENT);
    public static final String MEMBER_ADDED_TOPIC0 = EventEncoder.encode(Election.MEMBERADDED_EVENT);
    public static final String VOTE_ADDED_TOPIC0 = EventEncoder.encode(Election.VOTEADDED_EVENT);
    public static final String ELECTION_ENDED_TOPIC0 = EventEncoder.encode(Election.ELECTIONENDED_EVENT);

    private ElectionLogDecoder()
    {
    }

    public static ElectionStartedEvent decodeElectionStarted(Log log)
    {
        if (!hasTopic0(log, ELECTION_STARTED_TOPIC0))
        {
            return null;
        }

        Election.ElectionStartedEventResponse event = Election.getElectionStartedEventFromLog(log);
        return new ElectionStartedEvent(
                normalizeAddress(log.getAddress()),
                normalizeAddress(event.coordinator),
                event.startTime,
                event.endTime,
                log.getTransactionHash(),
                log.getBlockNumber(),
                safeLogIndex(log)
        );
    }

    public static VoteAddedEvent decodeVoteAdded(Log log)
    {
        if (!hasTopic0(log, VOTE_ADDED_TOPIC0))
        {
            return null;
        }

        Election.VoteAddedEventResponse event = Election.getVoteAddedEventFromLog(log);
        return new VoteAddedEvent(
                normalizeAddress(log.getAddress()),
                event.ciphertextHash,
                event.nullifier,
                event.ciphertext,
                log.getTransactionHash(),
                log.getBlockNumber(),
                safeLogIndex(log)
        );
    }

    public static MemberAddedEvent decodeMemberAdded(Log log)
    {
        if (!hasTopic0(log, MEMBER_ADDED_TOPIC0))
        {
            return null;
        }

        Election.MemberAddedEventResponse event = Election.getMemberAddedEventFromLog(log);
        return new MemberAddedEvent(
                normalizeAddress(log.getAddress()),
                event.groupId,
                event.index,
                event.identityCommitment,
                event.merkleTreeRoot,
                log.getTransactionHash(),
                log.getBlockNumber(),
                safeLogIndex(log)
        );
    }

    public static ElectionEndedEvent decodeElectionEnded(Log log)
    {
        if (!hasTopic0(log, ELECTION_ENDED_TOPIC0))
        {
            return null;
        }

        Election.ElectionEndedEventResponse event = Election.getElectionEndedEventFromLog(log);
        return new ElectionEndedEvent(
                normalizeAddress(log.getAddress()),
                normalizeAddress(event.coordinator),
                event.decryptionMaterial,
                log.getTransactionHash(),
                log.getBlockNumber(),
                safeLogIndex(log)
        );
    }

    private static boolean hasTopic0(Log log, String topic0)
    {
        return log.getTopics() != null
                && !log.getTopics().isEmpty()
                && Objects.equals(log.getTopics().get(0), topic0);
    }

    private static BigInteger safeLogIndex(Log log)
    {
        try
        {
            return log.getLogIndex();
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private static String normalizeAddress(String address)
    {
        if (address == null)
        {
            return null;
        }

        String value = address.trim();
        if (!value.startsWith("0x"))
        {
            value = "0x" + value;
        }
        return value.toLowerCase();
    }
}
