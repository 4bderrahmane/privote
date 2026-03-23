package org.privote.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

import java.math.BigInteger;

@Entity
@Table(
        name = "chain_sync_cursors",
        indexes = {
                @Index(name = "idx_chain_sync_cursor_stream", columnList = "stream_key", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChainSyncCursor
{

    @Id
    @Column(name = "stream_key", nullable = false, updatable = false, length = 128)
    private String streamKey;

    @Column(name = "last_processed_block", nullable = false, precision = 78, scale = 0)
    private BigInteger lastProcessedBlock;

    @Column(name = "last_processed_log_index", nullable = false, precision = 78, scale = 0)
    private BigInteger lastProcessedLogIndex;

    @Column(name = "last_processed_block_hash", length = 66)
    private String lastProcessedBlockHash;

    @Column(name = "updated_at")
    private Instant updatedAt;
}