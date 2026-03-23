package org.krino.voting_system.security;

import org.junit.jupiter.api.Test;
import org.krino.voting_system.configuration.SyncSecurityProperties;
import org.krino.voting_system.dto.citizen.CitizenSyncRequest;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SyncRequestAuthenticatorTest
{
    private static final String SECRET = "sync-secret";
    private static final Instant NOW = Instant.parse("2026-03-22T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final SyncSecurityProperties PROPERTIES = new SyncSecurityProperties(SECRET, 300, 600);

    @Test
    void acceptsValidSignedRequest()
    {
        SyncRequestAuthenticator authenticator = new SyncRequestAuthenticator(PROPERTIES, CLOCK);
        CitizenSyncRequest request = request();
        String timestamp = String.valueOf(NOW.getEpochSecond());
        String signature = "sha256=" + sign(SECRET, timestamp, request);

        assertDoesNotThrow(() -> authenticator.verifySyncRequest(timestamp, signature, request));
    }

    @Test
    void rejectsInvalidSignature()
    {
        SyncRequestAuthenticator authenticator = new SyncRequestAuthenticator(PROPERTIES, CLOCK);
        CitizenSyncRequest request = request();

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authenticator.verifySyncRequest(String.valueOf(NOW.getEpochSecond()), "sha256=deadbeef", request)
        );

        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void rejectsStaleTimestamp()
    {
        SyncRequestAuthenticator authenticator = new SyncRequestAuthenticator(PROPERTIES, CLOCK);
        CitizenSyncRequest request = request();
        String staleTimestamp = String.valueOf(NOW.minusSeconds(301).getEpochSecond());
        String signature = "sha256=" + sign(SECRET, staleTimestamp, request);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authenticator.verifySyncRequest(staleTimestamp, signature, request)
        );

        assertEquals(401, ex.getStatusCode().value());
    }

    @Test
    void rejectsReplayOfSameSignedPayload()
    {
        SyncRequestAuthenticator authenticator = new SyncRequestAuthenticator(PROPERTIES, CLOCK);
        CitizenSyncRequest request = request();
        String timestamp = String.valueOf(NOW.getEpochSecond());
        String signature = "sha256=" + sign(SECRET, timestamp, request);

        authenticator.verifySyncRequest(timestamp, signature, request);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authenticator.verifySyncRequest(timestamp, signature, request)
        );

        assertEquals(401, ex.getStatusCode().value());
    }

    private static CitizenSyncRequest request()
    {
        return new CitizenSyncRequest(
                UUID.fromString("22cf8839-0cb3-4ee6-bdd5-df5093fe9f95"),
                "user",
                "user@example.com",
                "John",
                "Doe",
                "CIN123",
                LocalDate.of(2001, 1, 2),
                "Casablanca",
                "+212600000000",
                true,
                true
        );
    }

    private static String sign(String secret, String timestamp, CitizenSyncRequest request)
    {
        String payload = String.join("\n",
                "ts=" + timestamp,
                "keycloakId=" + request.keycloakId(),
                "username=" + request.username(),
                "email=" + request.email(),
                "firstName=" + request.firstName(),
                "lastName=" + request.lastName(),
                "cin=" + request.cin(),
                "birthDate=" + request.birthDate(),
                "birthPlace=" + request.birthPlace(),
                "phoneNumber=" + request.phoneNumber(),
                "emailVerified=" + request.emailVerified(),
                "enabled=" + request.enabled()
        );

        try
        {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
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
}
