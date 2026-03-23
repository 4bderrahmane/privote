package org.privote.backend.web3.listener.cursor;

import java.util.Optional;

public interface CursorStore
{
    Optional<Cursor> load(String key);

    void save(String key, Cursor cursor);
}