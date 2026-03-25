package org.privote.synchronizer;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.AbstractKeycloakTransaction;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class CitizenSyncEventListener implements EventListenerProvider
{
    private static final Logger log = LoggerFactory.getLogger(CitizenSyncEventListener.class);
    private static final String BACKEND_SERVICE_URL_ENV = "BACKEND_SERVICE_URL";
    private static final String SYNC_SECRET_ENV = "SYNC_SECRET";
    private static final String DEFAULT_BACKEND_BASE_URL = "http://host.docker.internal:9090";
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final String TIMESTAMP_HEADER = "X-Sync-Timestamp";
    private static final String SIGNATURE_HEADER = "X-Sync-Signature";
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final AtomicBoolean warnedMissingSecret = new AtomicBoolean(false);
    private static final AtomicBoolean warnedMissingBackendUrl = new AtomicBoolean(false);
    private static final HttpClient DEFAULT_HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private final KeycloakSession session;
    private final HttpClient httpClient;
    private final Clock clock;

    public CitizenSyncEventListener(KeycloakSession session)
    {
        this(session, DEFAULT_HTTP_CLIENT, Clock.systemUTC());
    }

    CitizenSyncEventListener(KeycloakSession session, HttpClient httpClient, Clock clock)
    {
        this.session = session;
        this.httpClient = httpClient;
        this.clock = clock;
    }

    @Override
    public void onEvent(Event event)
    {
        if (event == null)
        {
            log.debug("[Keycloak-Sync] Ignoring null user event");
            return;
        }

        EventType eventType = event.getType();
        String userId = trimToNull(event.getUserId());
        if (eventType == null)
        {
            log.debug("[Keycloak-Sync] Ignoring user event without type userId={}", userId);
            return;
        }

        if (!shouldSync(eventType))
        {
            log.debug("[Keycloak-Sync] Ignoring unsupported user event type={} userId={}", eventType, userId);
            return;
        }

        log.info("[Keycloak-Sync] Received user event type={} userId={}", eventType, userId);
        syncUserAfterCommit(userId, describeUserTrigger(eventType));
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation)
    {
        if (adminEvent == null)
        {
            log.debug("[Keycloak-Sync] Ignoring null admin event");
            return;
        }

        if (!ResourceType.USER.equals(adminEvent.getResourceType()))
        {
            log.debug(
                    "[Keycloak-Sync] Ignoring admin event resourceType={} operation={} resourcePath={}",
                    adminEvent.getResourceType(),
                    adminEvent.getOperationType(),
                    adminEvent.getResourcePath()
            );
            return;
        }

        OperationType operation = adminEvent.getOperationType();
        if (!shouldSync(operation))
        {
            log.debug(
                    "[Keycloak-Sync] Ignoring admin user event operation={} resourcePath={} realmId={}",
                    operation,
                    adminEvent.getResourcePath(),
                    adminEvent.getRealmId()
            );
            return;
        }

        String resourcePath = trimToNull(adminEvent.getResourcePath());
        String triggerDescription = describeAdminTrigger(operation, resourcePath, adminEvent.getRealmId());
        log.info(
                "[Keycloak-Sync] Received admin event operation={} resourcePath={} realmId={}",
                operation,
                resourcePath,
                adminEvent.getRealmId()
        );

        String userId = extractUserIdFromResourcePath(resourcePath);
        if (userId == null)
        {
            log.warn("[Keycloak-Sync] Unable to extract user ID from {}", triggerDescription);
            return;
        }

        syncUserAfterCommit(userId, triggerDescription);
    }

    private boolean shouldSync(EventType eventType)
    {
        return EventType.REGISTER.equals(eventType)
                || EventType.UPDATE_PROFILE.equals(eventType)
                || EventType.VERIFY_EMAIL.equals(eventType);
    }

    private boolean shouldSync(OperationType operationType)
    {
        return OperationType.CREATE.equals(operationType)
                || OperationType.UPDATE.equals(operationType);
    }

    private void syncUserAfterCommit(String userId, String triggerDescription)
    {
        String normalizedUserId = trimToNull(userId);
        if (normalizedUserId == null)
        {
            log.warn("[Keycloak-Sync] Skipping sync because userId is missing for {}", triggerDescription);
            return;
        }

        RealmModel realm = session.getContext().getRealm();
        if (realm == null)
        {
            log.warn(
                    "[Keycloak-Sync] Skipping sync for user {} from {} because no realm is available in the session context",
                    normalizedUserId,
                    triggerDescription
            );
            return;
        }

        UserModel user = session.users().getUserById(realm, normalizedUserId);
        if (user == null)
        {
            log.warn(
                    "[Keycloak-Sync] Skipping sync for user {} from {} because the user was not found in realm {}",
                    normalizedUserId,
                    triggerDescription,
                    realm.getName()
            );
            return;
        }

        PreparedSyncRequest preparedRequest = prepareSyncRequest(user);
        if (preparedRequest == null)
        {
            log.warn(
                    "[Keycloak-Sync] Skipping sync for user {} from {} because payload preparation failed",
                    normalizedUserId,
                    triggerDescription
            );
            return;
        }

        log.info("[Keycloak-Sync] Queued sync for user {} from {}", normalizedUserId, triggerDescription);
        session.getTransactionManager().enlistAfterCompletion(new AbstractKeycloakTransaction()
        {
            @Override
            protected void commitImpl()
            {
                log.info("[Keycloak-Sync] Transaction committed; sending sync for user {} from {}", normalizedUserId, triggerDescription);
                sendToBackend(preparedRequest, normalizedUserId, triggerDescription);
            }

            @Override
            protected void rollbackImpl()
            {
                log.warn("[Keycloak-Sync] Transaction rolled back; skipping sync for user {} from {}", normalizedUserId, triggerDescription);
            }
        });
    }

    private PreparedSyncRequest prepareSyncRequest(UserModel user)
    {
        UUID keycloakId = parseUserId(user.getId());
        if (keycloakId == null)
        {
            return null;
        }

        String email = requiredValue("email", user.getEmail(), keycloakId);
        String firstName = requiredValue("firstName", user.getFirstName(), keycloakId);
        String lastName = requiredValue("lastName", user.getLastName(), keycloakId);
        String cin = requiredValue("cin", user.getFirstAttribute("cin"), keycloakId);
        if (email == null || firstName == null || lastName == null || cin == null)
        {
            return null;
        }

        SyncPayload payload = new SyncPayload(
                keycloakId,
                trimToNull(user.getUsername()),
                email,
                firstName,
                lastName,
                cin,
                normalizeBirthDate(user.getFirstAttribute("birthDate"), keycloakId),
                trimToNull(user.getFirstAttribute("birthPlace")),
                trimToNull(user.getFirstAttribute("phoneNumber")),
                user.isEmailVerified(),
                user.isEnabled()
        );

        return new PreparedSyncRequest(payload, toJson(payload));
    }

    private void sendToBackend(PreparedSyncRequest preparedRequest, String userIdForLogs, String triggerDescription)
    {
        log.debug("SYNC HIT");
        String backendBaseUrl = resolveBackendBaseUrl();
        String secret = resolveSecret();
        if (secret == null)
        {
            return;
        }

        long timestampSeconds = Instant.now(clock).getEpochSecond();
        String endpoint = buildEndpoint(backendBaseUrl);
        HttpRequest request;
        try
        {
            String signature = signatureHeaderValue(secret, timestampSeconds, preparedRequest.payload());
            request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header(TIMESTAMP_HEADER, String.valueOf(timestampSeconds))
                    .header(SIGNATURE_HEADER, signature)
                    .POST(HttpRequest.BodyPublishers.ofString(preparedRequest.jsonBody()))
                    .build();
        } catch (RuntimeException ex)
        {
            log.error(
                    "[Keycloak-Sync] Failed to create sync request for user {} from {} endpoint={}",
                    userIdForLogs,
                    triggerDescription,
                    endpoint,
                    ex
            );
            return;
        }

        log.info("[Keycloak-Sync] Sending sync for user {} to {} from {}", userIdForLogs, endpoint, triggerDescription);

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response ->
                {
                    int status = response.statusCode();
                    if (status / 100 == 2)
                    {
                        log.debug(
                                "[Keycloak-Sync] Sync completed for user {} status={} endpoint={} trigger={}",
                                userIdForLogs,
                                status,
                                endpoint,
                                triggerDescription
                        );
                        return;
                    }

                    log.error(
                            "[Keycloak-Sync] Sync failed for user {} status={} endpoint={} trigger={} body={}",
                            userIdForLogs,
                            status,
                            endpoint,
                            triggerDescription,
                            truncate(response.body())
                    );
                })
                .exceptionally(ex ->
                {
                    log.error(
                            "[Keycloak-Sync] Failed to sync user {} to {} from {}",
                            userIdForLogs,
                            endpoint,
                            triggerDescription,
                            ex
                    );
                    return null;
                });
    }

    private String resolveBackendBaseUrl()
    {
        String backendBaseUrl = trimToNull(System.getenv(BACKEND_SERVICE_URL_ENV));
        if (backendBaseUrl != null)
        {
            return backendBaseUrl;
        }

        if (warnedMissingBackendUrl.compareAndSet(false, true))
        {
            log.warn("[Keycloak-Sync] {} not set; defaulting to {}", BACKEND_SERVICE_URL_ENV, DEFAULT_BACKEND_BASE_URL);
        }
        return DEFAULT_BACKEND_BASE_URL;
    }

    private String resolveSecret()
    {
        String secret = trimToNull(System.getenv(SYNC_SECRET_ENV));
        if (secret != null)
        {
            return secret;
        }

        if (warnedMissingSecret.compareAndSet(false, true))
        {
            log.error("[Keycloak-Sync] {} is missing or blank. Sync is disabled.", SYNC_SECRET_ENV);
        }
        return null;
    }

    private String buildEndpoint(String backendBaseUrl)
    {
        return backendBaseUrl.endsWith("/")
                ? backendBaseUrl.substring(0, backendBaseUrl.length() - 1) + "/api/internal/sync"
                : backendBaseUrl + "/api/internal/sync";
    }

    private UUID parseUserId(String rawUserId)
    {
        if (rawUserId == null || rawUserId.isBlank())
        {
            return null;
        }

        try
        {
            return UUID.fromString(rawUserId.trim());
        } catch (IllegalArgumentException ex)
        {
            log.error("[Keycloak-Sync] Skipping sync because user ID is not a UUID: {}", rawUserId);
            return null;
        }
    }

    private String requiredValue(String fieldName, String value, UUID userId)
    {
        String normalized = trimToNull(value);
        if (normalized == null)
        {
            log.error("[Keycloak-Sync] Missing required field '{}' for user {}", fieldName, userId);
        }
        return normalized;
    }

    private String normalizeBirthDate(String rawBirthDate, UUID userId)
    {
        String normalized = trimToNull(rawBirthDate);
        if (normalized == null)
        {
            return null;
        }

        try
        {
            return LocalDate.parse(normalized).toString();
        } catch (DateTimeParseException ex)
        {
            log.warn("[Keycloak-Sync] Invalid birthDate '{}' for user {}", rawBirthDate, userId);
            return null;
        }
    }

    private String describeUserTrigger(EventType eventType)
    {
        return "user event type=" + eventType;
    }

    private String describeAdminTrigger(OperationType operationType, String resourcePath, String realmId)
    {
        return "admin event operation=" + operationType
                + " resourcePath=" + resourcePath
                + " realmId=" + realmId;
    }

    private String extractUserIdFromResourcePath(String resourcePath)
    {
        String normalizedResourcePath = trimToNull(resourcePath);
        if (normalizedResourcePath == null)
        {
            return null;
        }

        String[] parts = normalizedResourcePath.split("/");
        if (parts.length < 2 || !"users".equals(parts[0]))
        {
            return null;
        }
        return trimToNull(parts[1]);
    }

    private String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String signatureHeaderValue(String secret, long timestampSeconds, SyncPayload payload)
    {
        return SIGNATURE_PREFIX + toHex(sign(secret, canonicalPayload(timestampSeconds, payload)));
    }

    private String canonicalPayload(long timestampSeconds, SyncPayload payload)
    {
        return String.join("\n",
                "ts=" + timestampSeconds,
                "keycloakId=" + payload.keycloakId(),
                "username=" + normalize(payload.username()),
                "email=" + normalize(payload.email()),
                "firstName=" + normalize(payload.firstName()),
                "lastName=" + normalize(payload.lastName()),
                "cin=" + normalize(payload.cin()),
                "birthDate=" + normalize(payload.birthDate()),
                "birthPlace=" + normalize(payload.birthPlace()),
                "phoneNumber=" + normalize(payload.phoneNumber()),
                "emailVerified=" + payload.emailVerified(),
                "enabled=" + payload.enabled()
        );
    }

    private byte[] sign(String secret, String payload)
    {
        try
        {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex)
        {
            throw new IllegalStateException("Unable to sign sync request", ex);
        }
    }

    private String toHex(byte[] bytes)
    {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes)
        {
            builder.append(String.format(Locale.ROOT, "%02x", value));
        }
        return builder.toString();
    }

    private String toJson(SyncPayload payload)
    {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{");
        appendField(sb, "keycloakId", payload.keycloakId().toString());
        sb.append(",");
        appendField(sb, "username", payload.username());
        sb.append(",");
        appendField(sb, "email", payload.email());
        sb.append(",");
        appendField(sb, "firstName", payload.firstName());
        sb.append(",");
        appendField(sb, "lastName", payload.lastName());
        sb.append(",");
        appendField(sb, "cin", payload.cin());
        sb.append(",");
        appendField(sb, "birthDate", payload.birthDate());
        sb.append(",");
        appendField(sb, "birthPlace", payload.birthPlace());
        sb.append(",");
        appendField(sb, "phoneNumber", payload.phoneNumber());
        sb.append(",");
        sb.append("\"emailVerified\":").append(payload.emailVerified());
        sb.append(",");
        sb.append("\"enabled\":").append(payload.enabled());
        sb.append("}");
        return sb.toString();
    }

    private void appendField(StringBuilder sb, String key, String value)
    {
        sb.append("\"").append(escapeJson(key)).append("\":");
        if (value == null)
        {
            sb.append("null");
        } else
        {
            sb.append("\"").append(escapeJson(value)).append("\"");
        }
    }

    private String escapeJson(String value)
    {
        if (value == null)
        {
            return "";
        }

        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);
            switch (c)
            {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default ->
                {
                    if (c < 0x20)
                    {
                        out.append(String.format("\\u%04x", (int) c));
                    } else
                    {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private String normalize(String value)
    {
        return value == null ? "" : value.trim();
    }

    private String truncate(String body)
    {
        if (body == null || body.length() <= 1000)
        {
            return body;
        }
        return body.substring(0, 1000) + "...";
    }

    @Override
    public void close()
    {
    }

    private record PreparedSyncRequest(SyncPayload payload, String jsonBody)
    {
    }

    private record SyncPayload(
            UUID keycloakId,
            String username,
            String email,
            String firstName,
            String lastName,
            String cin,
            String birthDate,
            String birthPlace,
            String phoneNumber,
            boolean emailVerified,
            boolean enabled
    )
    {
    }
}
