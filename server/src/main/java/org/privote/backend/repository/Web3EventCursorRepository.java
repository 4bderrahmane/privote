package org.privote.backend.repository;

import org.privote.backend.entity.ChainSyncCursor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Web3EventCursorRepository extends JpaRepository<ChainSyncCursor, String>
{
}
