package org.krino.voting_system.web3.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.krino.voting_system.web3.config.Web3jProperties;
import org.krino.voting_system.web3.contracts.ElectionFactory;
import org.krino.voting_system.web3.listener.cursor.Cursor;
import org.krino.voting_system.web3.listener.cursor.CursorStore;
import org.krino.voting_system.web3.listener.events.ElectionDeployedEvent;
import org.krino.voting_system.web3.util.Web3Types;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.abi.EventEncoder;
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
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElectionEventListener
{

    private static final String CURSOR_KEY = "ElectionFactory.ElectionDeployed";
    private static final BigInteger MAX_BLOCK_SPAN = BigInteger.valueOf(2_000);
    private final Web3j web3j;
    private final Web3jProperties props;
    private final CursorStore cursorStore;
    private final ElectionEventHandler handler;

    @Scheduled(fixedDelayString = "${web3j.listener.poll-interval-ms:5000}")
    public void pollElectionDeployed()
    {
        final String factoryAddress = normalizeAddress(props.getElectionFactoryAddress());
        if (factoryAddress == null) return;

        final BigInteger safeTo;
        try
        {
            safeTo = computeSafeToBlock();
        } catch (IOException e)
        {
            log.warn("Failed to read latest block number (RPC error): {}", e.getMessage(), e);
            return;
        }

        if (safeTo.signum() < 0) return;

        Cursor cursor;
        try
        {
            cursor = validateCursor(loadOrInitCursor());
        } catch (IOException e)
        {
            log.warn("Failed to validate web3 listener cursor: {}", e.getMessage(), e);
            return;
        }

        if (cursor.nextBlock().compareTo(safeTo) > 0) return;

        // IMPORTANT: resume filter should be based on the INITIAL cursor only
        // (skip logIndex < resumeLogIndex only on resumeBlock).
        final BigInteger resumeBlock = cursor.nextBlock();
        final BigInteger resumeLogIndex = cursor.nextLogIndex();
        boolean applyResumeFilter = true;

        BigInteger fromBlock = cursor.nextBlock();

        while (fromBlock.compareTo(safeTo) <= 0)
        {
            BigInteger toBlock = min(fromBlock.add(MAX_BLOCK_SPAN), safeTo);

            try
            {
                List<Log> logs = fetchElectionDeployedLogs(factoryAddress, fromBlock, toBlock);

                // Process logs; do NOT advance cursor past a failing log.
                cursor = processLogs(cursor, logs, applyResumeFilter ? resumeBlock : null, applyResumeFilter ? resumeLogIndex : BigInteger.ZERO);

                // After first chunk, resume filtering is no longer needed.
                applyResumeFilter = false;

                // If no events occurred, we still want to move the cursor forward,
                // otherwise we will re-scan the same empty block range on the next poll.
                cursor = cursor.advancePastBlock(toBlock, fetchBlockHash(toBlock));
                saveCursor(cursor);

                fromBlock = toBlock.add(BigInteger.ONE);

            } catch (IOException e)
            {
                log.warn("eth_getLogs RPC error ({}..{}): {}", fromBlock, toBlock, e.getMessage(), e);
                return; // don't advance cursor on RPC error; retry next poll
            } catch (RuntimeException e)
            {
                log.error("Listener failed while processing logs ({}..{}): {}", fromBlock, toBlock, e.getMessage(), e);
                return; // don't advance cursor past the failing log
            }
        }
    }

    private BigInteger computeSafeToBlock() throws IOException
    {
        EthBlockNumber bn = web3j.ethBlockNumber().send();
        BigInteger latest = bn.getBlockNumber();

        int confirmations = Math.max(0, props.getConfirmations());
        return latest.subtract(BigInteger.valueOf(confirmations));
    }

    private Cursor loadOrInitCursor()
    {
        return cursorStore.load(CURSOR_KEY).orElseGet(() -> Cursor.initial(props.getStartBlock()));
    }

    private void saveCursor(Cursor cursor)
    {
        cursorStore.save(CURSOR_KEY, cursor);
    }

    private List<Log> fetchElectionDeployedLogs(String factoryAddress, BigInteger from, BigInteger to) throws IOException
    {
        EthFilter filter = new EthFilter(DefaultBlockParameter.valueOf(from), DefaultBlockParameter.valueOf(to), factoryAddress);
        filter.addSingleTopic(EventEncoder.encode(ElectionFactory.ELECTIONDEPLOYED_EVENT));

        EthLog ethLog = web3j.ethGetLogs(filter).send();
        if (ethLog.hasError())
        {
            // Treat the provider-reported error as an IOException so the caller doesn't advance the cursor.
            throw new IOException(ethLog.getError().getMessage());
        }

        @SuppressWarnings("rawtypes") List<EthLog.LogResult> results = ethLog.getLogs();
        if (results == null || results.isEmpty()) return List.of();

        List<Log> logs = new ArrayList<>(results.size());
        for (EthLog.LogResult<?> lr : results)
        {
            Object raw = lr.get();
            if (raw instanceof Log logObj)
            {
                logs.add(logObj);
            }
        }
        return logs;
    }

    private Cursor processLogs(Cursor cursor, List<Log> logs, BigInteger resumeBlock, BigInteger resumeLogIndex)
    {
        Cursor current = cursor;

        for (Log logObj : logs)
        {
            BigInteger bn = logObj.getBlockNumber();
            BigInteger li = logObj.getLogIndex();

            ElectionDeployedEvent event = shouldSkip(bn, li, resumeBlock, resumeLogIndex, current) ? null : decodeElectionDeployed(logObj).orElse(null);

            if (event == null)
            {
                continue;
            }

            handler.onElectionDeployed(event);

            current = current.advanceToProcessedLog(bn, li, normalizeHash(logObj.getBlockHash()));
            saveCursor(current);
        }

        return current;
    }


    private boolean shouldSkip(BigInteger blockNumber, BigInteger logIndex, BigInteger resumeBlock, BigInteger resumeLogIndex, Cursor currentCursor)
    {
        if (blockNumber.compareTo(currentCursor.nextBlock()) < 0) return true;

        if (blockNumber.equals(currentCursor.nextBlock()) && logIndex.compareTo(currentCursor.nextLogIndex()) < 0)
        {
            return true;
        }

        return resumeBlock != null && resumeBlock.equals(blockNumber) && logIndex.compareTo(resumeLogIndex) < 0;
    }


    private Optional<ElectionDeployedEvent> decodeElectionDeployed(Log logObj)
    {
        if (logObj.getTopics() == null || logObj.getTopics().isEmpty()) return Optional.empty();

        String topic0 = logObj.getTopics().getFirst();
        String expected = EventEncoder.encode(ElectionFactory.ELECTIONDEPLOYED_EVENT);

        if (!expected.equalsIgnoreCase(topic0)) return Optional.empty();

        var ev = ElectionFactory.getElectionDeployedEventFromLog(logObj);

        UUID uuid = Web3Types.bytes16ToUuid(ev.uuid);

        return Optional.of(new ElectionDeployedEvent(uuid, ev.externalNullifier, normalizeAddress(ev.coordinator), normalizeAddress(ev.election), ev.endTime, logObj.getTransactionHash(), logObj.getBlockNumber(), logObj.getLogIndex()));
    }


    private Cursor validateCursor(Cursor cursor) throws IOException
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
                "Cursor block hash mismatch for key={}. persistedBlock={}, persistedHash={}, chainHash={}. Rewinding to block {}.",
                CURSOR_KEY,
                cursor.lastProcessedBlock(),
                cursor.lastProcessedBlockHash(),
                currentHash,
                rewound.lastProcessedBlock()
        );
        saveCursor(rewound);
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

    private static BigInteger min(BigInteger a, BigInteger b)
    {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static String normalizeAddress(String address)
    {
        if (address == null) return null;
        String a = address.trim();
        if (!a.startsWith("0x")) a = "0x" + a;
        if (a.length() != 42) return null;
        return a.toLowerCase();
    }

    private static String normalizeHash(String hash)
    {
        if (hash == null) return null;
        String value = hash.trim();
        if (value.isEmpty()) return null;
        if (!value.startsWith("0x")) value = "0x" + value;
        return value.toLowerCase();
    }
}
