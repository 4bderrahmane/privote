package org.privote.backend.web3.listener.cursor;

import java.math.BigInteger;
import java.util.Objects;

public record Cursor(
        BigInteger lastProcessedBlock,
        BigInteger lastProcessedLogIndex,
        String lastProcessedBlockHash)
{
    public static final BigInteger NO_LOG_INDEX = BigInteger.valueOf(-1);

    public Cursor
    {
        Objects.requireNonNull(lastProcessedBlock, "lastProcessedBlock");
        Objects.requireNonNull(lastProcessedLogIndex, "lastProcessedLogIndex");
    }

    public static Cursor initial(BigInteger startBlock)
    {
        BigInteger effectiveStart = startBlock == null ? BigInteger.ZERO : startBlock.max(BigInteger.ZERO);
        return new Cursor(effectiveStart.subtract(BigInteger.ONE), NO_LOG_INDEX, null);
    }

    public BigInteger nextBlock()
    {
        return hasOpenBlock() ? lastProcessedBlock : lastProcessedBlock.add(BigInteger.ONE);
    }

    public BigInteger nextLogIndex()
    {
        return hasOpenBlock() ? lastProcessedLogIndex.add(BigInteger.ONE) : BigInteger.ZERO;
    }

    public boolean hasProcessedBlock()
    {
        return lastProcessedBlock.signum() >= 0;
    }

    public boolean hasOpenBlock()
    {
        return lastProcessedLogIndex.signum() >= 0;
    }

    public Cursor advanceToProcessedLog(BigInteger blockNumber, BigInteger logIndex, String blockHash)
    {
        return new Cursor(blockNumber, logIndex, blockHash);
    }

    public Cursor advancePastBlock(BigInteger blockNumber, String blockHash)
    {
        return new Cursor(blockNumber, NO_LOG_INDEX, blockHash);
    }

    public Cursor rewindOneBlock(BigInteger startBlock)
    {
        BigInteger effectiveStart = startBlock == null ? BigInteger.ZERO : startBlock.max(BigInteger.ZERO);
        BigInteger floor = effectiveStart.subtract(BigInteger.ONE);
        BigInteger rewoundBlock = lastProcessedBlock.subtract(BigInteger.ONE);
        if (rewoundBlock.compareTo(floor) < 0)
        {
            rewoundBlock = floor;
        }
        return new Cursor(rewoundBlock, NO_LOG_INDEX, null);
    }
}
