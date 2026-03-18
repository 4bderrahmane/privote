package org.krino.voting_system.web3.listener.cursor;

import lombok.RequiredArgsConstructor;
import org.krino.voting_system.entity.ChainSyncCursor;
import org.krino.voting_system.repository.Web3EventCursorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .map(e -> new Cursor(e.getLastProcessedBlock(), e.getLastProcessedLogIndex()));
    }

    @Override
    @Transactional
    public void save(String key, BigInteger nextBlock, BigInteger nextLogIndex)
    {
        ChainSyncCursor entity = repo.findById(key).orElseGet(ChainSyncCursor::new);

        entity.setStreamKey(key);
        entity.setLastProcessedBlock(nextBlock);
        entity.setLastProcessedLogIndex(nextLogIndex);

        repo.save(entity);
    }
}
