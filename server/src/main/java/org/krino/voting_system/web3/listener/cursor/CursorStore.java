package org.krino.voting_system.web3.listener.cursor;

import java.math.BigInteger;
import java.util.Optional;

public interface CursorStore
{
    Optional<Cursor> load(String key);

    void save(String key, Cursor cursor);
}