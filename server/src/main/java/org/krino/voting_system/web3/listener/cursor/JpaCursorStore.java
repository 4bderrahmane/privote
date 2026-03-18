package org.krino.voting_system.web3.listener.cursor;

import lombok.RequiredArgsConstructor;
import org.krino.voting_system.entity.ChainSyncCursor;
import org.krino.voting_system.repository.Web3EventCursorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.math.BigInteger;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class JpaCursorStore implements CursorStore
{

    private final Web3EventCursorRepository repo;

    @Override
    public Optional<Cursor> load(String key)
    {
        return repo.findById(key)
                .map(this::toCursor);
    }

    @Override
    @Transactional
    public void save(String key, Cursor cursor)
    {
        ChainSyncCursor entity = repo.findById(key).orElseGet(ChainSyncCursor::new);

        entity.setStreamKey(key);
        entity.setLastProcessedBlock(cursor.lastProcessedBlock());
        entity.setLastProcessedLogIndex(cursor.lastProcessedLogIndex());
        entity.setLastProcessedBlockHash(cursor.lastProcessedBlockHash());
        entity.setUpdatedAt(Instant.now());

        repo.save(entity);
    }

    private Cursor toCursor(ChainSyncCursor entity)
    {
        BigInteger persistedBlock = entity.getLastProcessedBlock() == null ? BigInteger.ZERO : entity.getLastProcessedBlock();
        BigInteger persistedLogIndex = entity.getLastProcessedLogIndex() == null ? Cursor.NO_LOG_INDEX : entity.getLastProcessedLogIndex();

        if (entity.getLastProcessedBlockHash() == null)
        {
            return fromLegacyNextPointer(persistedBlock, persistedLogIndex);
        }

        return new Cursor(persistedBlock, persistedLogIndex, entity.getLastProcessedBlockHash());
    }

    private Cursor fromLegacyNextPointer(BigInteger nextBlock, BigInteger nextLogIndex)
    {
        if (nextLogIndex.signum() > 0)
        {
            return new Cursor(nextBlock, nextLogIndex.subtract(BigInteger.ONE), null);
        }

        return new Cursor(nextBlock.subtract(BigInteger.ONE), Cursor.NO_LOG_INDEX, null);
    }
}
