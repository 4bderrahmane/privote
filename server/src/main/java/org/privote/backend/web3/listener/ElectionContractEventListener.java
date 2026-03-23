package org.privote.backend.web3.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.privote.backend.entity.Election;
import org.privote.backend.repository.ElectionRepository;
import org.privote.backend.web3.config.Web3jProperties;
import org.privote.backend.web3.listener.cursor.Cursor;
import org.privote.backend.web3.listener.cursor.CursorStore;
import org.privote.backend.web3.listener.events.ElectionEndedEvent;
import org.privote.backend.web3.listener.events.ElectionStartedEvent;
import org.privote.backend.web3.listener.events.MemberAddedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3j.listener", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ElectionContractEventListener
{
    private static final String CURSOR_KEY_PREFIX = "ElectionContract.";
    private static final BigInteger MAX_BLOCK_SPAN = BigInteger.valueOf(2_000);

    private final Web3j web3j;
    private final Web3jProperties props;
    private final CursorStore cursorStore;
    private final ElectionRepository electionRepository;
    private final ElectionEventHandler handler;

    @Scheduled(fixedDelayString = "${web3j.listener.poll-interval-ms:5000}")
    public void pollElectionContractEvents()
    {
        final BigInteger safeTo;
        try
        {
            safeTo = computeSafeToBlock();
        }
        catch (IOException e)
        {
            log.warn("Failed to read latest block number for election contracts: {}", e.getMessage(), e);
            return;
        }

        if (safeTo.signum() < 0)
        {
            return;
        }

        for (Election election : electionRepository.findByContractAddressIsNotNull())
        {
            String contract = normalizeAddress(election.getContractAddress());
            if (contract == null)
            {
                continue;
            }

            pollSingleElection(contract, safeTo);
        }
    }

    private void pollSingleElection(String contractAddress, BigInteger safeTo)
    {
        Cursor cursor;
        try
        {
            cursor = validateCursor(contractAddress, loadOrInitCursor(contractAddress));
        }
        catch (IOException e)
        {
            log.warn("Failed to validate election cursor for {}: {}", contractAddress, e.getMessage(), e);
            return;
        }

        if (cursor.nextBlock().compareTo(safeTo) > 0)
        {
            return;
        }

        final BigInteger resumeBlock = cursor.nextBlock();
        final BigInteger resumeLogIndex = cursor.nextLogIndex();
        boolean applyResumeFilter = true;
        BigInteger fromBlock = cursor.nextBlock();

        while (fromBlock.compareTo(safeTo) <= 0)
        {
            BigInteger toBlock = min(fromBlock.add(MAX_BLOCK_SPAN), safeTo);
            try
            {
                List<Log> logs = fetchContractLogs(contractAddress, fromBlock, toBlock);
                cursor = processLogs(
                        contractAddress,
                        cursor,
                        logs,
                        applyResumeFilter ? resumeBlock : null,
                        applyResumeFilter ? resumeLogIndex : BigInteger.ZERO
                );

                applyResumeFilter = false;
                cursor = cursor.advancePastBlock(toBlock, fetchBlockHash(toBlock));
                saveCursor(contractAddress, cursor);
                fromBlock = toBlock.add(BigInteger.ONE);
            }
            catch (IOException e)
            {
                log.warn("eth_getLogs RPC error for contract={} ({}..{}): {}", contractAddress, fromBlock, toBlock, e.getMessage(), e);
                return;
            }
            catch (RuntimeException e)
            {
                log.error("Election contract listener failed for contract={} ({}..{}): {}", contractAddress, fromBlock, toBlock, e.getMessage(), e);
                return;
            }
        }
    }

    private Cursor processLogs(String contractAddress, Cursor cursor, List<Log> logs, BigInteger resumeBlock, BigInteger resumeLogIndex)
    {
        Cursor current = cursor;

        for (Log logObj : logs)
        {
            BigInteger blockNumber = logObj.getBlockNumber();
            BigInteger logIndex = logObj.getLogIndex();

            if (shouldSkip(blockNumber, logIndex, resumeBlock, resumeLogIndex, current))
            {
                continue;
            }

            MemberAddedEvent memberAddedEvent = ElectionLogDecoder.decodeMemberAdded(logObj);
            if (memberAddedEvent != null)
            {
                handler.onMemberAdded(memberAddedEvent);
                current = current.advanceToProcessedLog(blockNumber, logIndex, normalizeHash(logObj.getBlockHash()));
                saveCursor(contractAddress, current);
                continue;
            }

            ElectionStartedEvent startedEvent = ElectionLogDecoder.decodeElectionStarted(logObj);
            if (startedEvent != null)
            {
                handler.onElectionStarted(startedEvent);
                current = current.advanceToProcessedLog(blockNumber, logIndex, normalizeHash(logObj.getBlockHash()));
                saveCursor(contractAddress, current);
                continue;
            }

            ElectionEndedEvent endedEvent = ElectionLogDecoder.decodeElectionEnded(logObj);
            if (endedEvent != null)
            {
                handler.onElectionEnded(endedEvent);
                current = current.advanceToProcessedLog(blockNumber, logIndex, normalizeHash(logObj.getBlockHash()));
                saveCursor(contractAddress, current);
            }
        }

        return current;
    }

    private List<Log> fetchContractLogs(String contractAddress, BigInteger from, BigInteger to) throws IOException
    {
        EthFilter filter = new EthFilter(
                DefaultBlockParameter.valueOf(from),
                DefaultBlockParameter.valueOf(to),
                contractAddress
        );

        EthLog ethLog = web3j.ethGetLogs(filter).send();
        if (ethLog.hasError())
        {
            throw new IOException(ethLog.getError().getMessage());
        }

        @SuppressWarnings("rawtypes") List<EthLog.LogResult> results = ethLog.getLogs();
        if (results == null || results.isEmpty())
        {
            return List.of();
        }

        List<Log> logs = new ArrayList<>(results.size());
        for (EthLog.LogResult<?> logResult : results)
        {
            Object raw = logResult.get();
            if (raw instanceof Log logObj)
            {
                logs.add(logObj);
            }
        }

        return logs;
    }

    private BigInteger computeSafeToBlock() throws IOException
    {
        EthBlockNumber bn = web3j.ethBlockNumber().send();
        BigInteger latest = bn.getBlockNumber();
        int confirmations = Math.max(0, props.getConfirmations());
        return latest.subtract(BigInteger.valueOf(confirmations));
    }

    private Cursor loadOrInitCursor(String contractAddress)
    {
        return cursorStore.load(cursorKey(contractAddress))
                .orElseGet(() -> Cursor.initial(props.getStartBlock()));
    }

    private void saveCursor(String contractAddress, Cursor cursor)
    {
        cursorStore.save(cursorKey(contractAddress), cursor);
    }

    private Cursor validateCursor(String contractAddress, Cursor cursor) throws IOException
    {
        if (!cursor.hasProcessedBlock() || cursor.lastProcessedBlockHash() == null)
        {
            return cursor;
        }

        String currentHash = fetchBlockHash(cursor.lastProcessedBlock());
        if (currentHash != null && currentHash.equalsIgnoreCase(cursor.lastProcessedBlockHash()))
        {
            return cursor;
        }

        Cursor rewound = cursor.rewindOneBlock(props.getStartBlock());
        log.warn(
                "Election cursor block hash mismatch for contract={}. persistedBlock={}, persistedHash={}, chainHash={}. Rewinding to block {}.",
                contractAddress,
                cursor.lastProcessedBlock(),
                cursor.lastProcessedBlockHash(),
                currentHash,
                rewound.lastProcessedBlock()
        );
        saveCursor(contractAddress, rewound);
        return rewound;
    }

    private String fetchBlockHash(BigInteger blockNumber) throws IOException
    {
        EthBlock response = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(blockNumber), false).send();
        if (response.hasError())
        {
            throw new IOException(response.getError().getMessage());
        }

        EthBlock.Block block = response.getBlock();
        return block == null ? null : normalizeHash(block.getHash());
    }

    private static boolean shouldSkip(BigInteger blockNumber, BigInteger logIndex, BigInteger resumeBlock, BigInteger resumeLogIndex, Cursor currentCursor)
    {
        if (blockNumber.compareTo(currentCursor.nextBlock()) < 0)
        {
            return true;
        }

        if (blockNumber.equals(currentCursor.nextBlock()) && logIndex.compareTo(currentCursor.nextLogIndex()) < 0)
        {
            return true;
        }

        return resumeBlock != null && resumeBlock.equals(blockNumber) && logIndex.compareTo(resumeLogIndex) < 0;
    }

    private static String cursorKey(String contractAddress)
    {
        return CURSOR_KEY_PREFIX + contractAddress.toLowerCase();
    }

    private static BigInteger min(BigInteger a, BigInteger b)
    {
        return a.compareTo(b) <= 0 ? a : b;
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
        if (value.length() != 42)
        {
            return null;
        }
        return value.toLowerCase();
    }

    private static String normalizeHash(String hash)
    {
        if (hash == null)
        {
            return null;
        }

        String value = hash.trim();
        if (value.isEmpty())
        {
            return null;
        }
        if (!value.startsWith("0x"))
        {
            value = "0x" + value;
        }
        return value.toLowerCase();
    }
}
