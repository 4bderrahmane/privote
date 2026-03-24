package org.privote.backend.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sync")
public record SyncSecurityProperties(String secret, long maxSkewSeconds, long replayWindowSeconds)
{
    private static final long DEFAULT_MAX_SKEW_SECONDS = 300L;
    private static final long DEFAULT_REPLAY_WINDOW_SECONDS = 600L;

    public SyncSecurityProperties
    {
        if (secret == null || secret.isBlank())
        {
            throw new IllegalStateException("sync.secret must be configured");
        }

        if (maxSkewSeconds == 0)
        {
            maxSkewSeconds = DEFAULT_MAX_SKEW_SECONDS;
        } else if (maxSkewSeconds < 0)
        {
            throw new IllegalArgumentException("sync.max-skew-seconds must be > 0");
        }

        if (replayWindowSeconds == 0)
        {
            replayWindowSeconds = DEFAULT_REPLAY_WINDOW_SECONDS;
        } else if (replayWindowSeconds < 0)
        {
            throw new IllegalArgumentException("sync.replay-window-seconds must be > 0");
        }
    }
}
