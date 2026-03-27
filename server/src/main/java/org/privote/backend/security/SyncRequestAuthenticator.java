package org.privote.backend.security;

import lombok.RequiredArgsConstructor;
import org.privote.backend.configuration.SyncSecurityProperties;
import org.privote.backend.dto.citizen.CitizenSyncRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@RequiredArgsConstructor
public class SyncRequestAuthenticator
{
    private static final String INVALID_SYNC_REQUEST = "Invalid sync request";
    private static final String SYNC_TIMESTAMP = "sync timestamp";
    private static final String SYNC_SIGNATURE = "sync signature";
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final SyncSecurityProperties syncSecurityProperties;
    private final Clock clock;
    private final ConcurrentMap<String, Long> seenSignatures = new ConcurrentHashMap<>();

    private static String canonicalPayload(long timestampSeconds, CitizenSyncRequest request)
    {
        return String.join("\n",
                "ts=" + timestampSeconds,
                "keycloakId=" + valueOf(request.keycloakId()),
                "username=" + normalize(request.username()),
                "email=" + normalize(request.email()),
                "firstName=" + normalize(request.firstName()),
                "lastName=" + normalize(request.lastName()),
                "cin=" + normalize(request.cin()),
                "birthDate=" + valueOf(request.birthDate()),
                "birthPlace=" + normalize(request.birthPlace()),
                "phoneNumber=" + normalize(request.phoneNumber()),
                "emailVerified=" + request.emailVerified(),
                "enabled=" + request.enabled()
        );
    }

    private static String normalize(String value)
    {
        return value == null ? "" : value.trim();
    }

    private static String valueOf(Object value)
    {
        if (value instanceof LocalDate localDate)
        {
            return localDate.toString();
        }
        return value == null ? "" : value.toString();
    }

    private static byte[] fromHex(String hex)
    {
        String normalized = hex.toLowerCase(Locale.ROOT);
        int length = normalized.length();
        if (length % 2 != 0)
        {
            throw new IllegalArgumentException("Hex length must be even");
        }

        byte[] bytes = new byte[length / 2];
        for (int i = 0; i < length; i += 2)
        {
            int high = Character.digit(normalized.charAt(i), 16);
            int low = Character.digit(normalized.charAt(i + 1), 16);
            if (high < 0 || low < 0)
            {
                throw new IllegalArgumentException("Invalid hex signature");
            }
            bytes[i / 2] = (byte) ((high << 4) + low);
        }
        return bytes;
    }

    private static String toHex(byte[] bytes)
    {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes)
        {
            builder.append(String.format(Locale.ROOT, "%02x", value));
        }
        return builder.toString();
    }

    private static void unauthorized(String message)
    {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }

    public void verifySyncRequest(String timestampHeader, String signatureHeader, CitizenSyncRequest request)
    {
        if (request == null)
        {
            unauthorized(INVALID_SYNC_REQUEST);
        }

        long timestampSeconds = parseTimestamp(timestampHeader);
        long nowSeconds = Instant.now(clock).getEpochSecond();

        if (Math.abs(nowSeconds - timestampSeconds) > syncSecurityProperties.maxSkewSeconds())
        {
            unauthorized("Stale sync request");
        }

        byte[] providedSignature = parseSignature(signatureHeader);
        byte[] expectedSignature = sign(canonicalPayload(timestampSeconds, request));
        if (!MessageDigest.isEqual(providedSignature, expectedSignature))
        {
            unauthorized(INVALID_SYNC_REQUEST);
        }

        evictExpiredEntries(nowSeconds, syncSecurityProperties.replayWindowSeconds());
        String replayKey = timestampSeconds + ":" + toHex(providedSignature);
        Long previous = seenSignatures.putIfAbsent(replayKey, nowSeconds);
        if (previous != null)
        {
            unauthorized("Replay detected");
        }
    }

    private long parseTimestamp(String timestampHeader)
    {
        if (timestampHeader == null || timestampHeader.isBlank())
        {
            unauthorized("Missing " + SYNC_TIMESTAMP);
        }

        try
        {
            return Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException ex)
        {
            unauthorized("Invalid " + SYNC_TIMESTAMP);
            return -1L;
        }
    }

    private byte[] parseSignature(String signatureHeader)
    {
        if (signatureHeader == null || signatureHeader.isBlank())
        {
            unauthorized("Missing " + SYNC_SIGNATURE);
        }

        String raw = signatureHeader.trim();
        if (raw.regionMatches(true, 0, SIGNATURE_PREFIX, 0, SIGNATURE_PREFIX.length()))
        {
            raw = raw.substring(SIGNATURE_PREFIX.length()).trim();
        }

        if (raw.length() != 64)
        {
            unauthorized(INVALID_SYNC_REQUEST);
        }

        try
        {
            return fromHex(raw);
        } catch (IllegalArgumentException ex)
        {
            unauthorized("Invalid " + SYNC_SIGNATURE);
            return new byte[0];
        }
    }

    private byte[] sign(String payload)
    {
        try
        {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(syncSecurityProperties.secret().getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex)
        {
            throw new IllegalStateException("Unable to verify " + SYNC_SIGNATURE, ex);
        }
    }

    private void evictExpiredEntries(long nowSeconds, long replayWindowSeconds)
    {
        for (Map.Entry<String, Long> entry : seenSignatures.entrySet())
        {
            if ((nowSeconds - entry.getValue()) > replayWindowSeconds)
            {
                seenSignatures.remove(entry.getKey(), entry.getValue());
            }
        }
    }
}
