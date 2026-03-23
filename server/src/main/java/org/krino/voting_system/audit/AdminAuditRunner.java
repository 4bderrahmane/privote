package org.krino.voting_system.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.krino.voting_system.entity.enums.SystemLogAction;
import org.krino.voting_system.entity.enums.SystemLogOutcome;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuditRunner
{
    private final AuditLogService auditLogService;

    public static AuditContext context(
            UUID adminKeycloakId,
            SystemLogAction action,
            String targetType,
            @Nullable String targetId
    )
    {
        return new AuditContext(adminKeycloakId, action, targetType, targetId);
    }

    public <T> T run(
            AuditContext context,
            Supplier<T> operation,
            Function<T, @Nullable String> successTargetId,
            Function<T, @Nullable String> successDetails,
            Function<RuntimeException, @Nullable String> failureDetails
    )
    {
        try
        {
            T result = operation.get();
            String successfulTargetId = successTargetId.apply(result);
            String successfulDetails = successDetails.apply(result);
            auditLogService.logAction(
                    context.adminKeycloakId(),
                    context.action(),
                    SystemLogOutcome.SUCCESS,
                    context.targetType(),
                    successfulTargetId,
                    successfulDetails
            );
            log.info("Admin action succeeded action={} actorId={} targetType={} targetId={}",
                    context.action(), context.adminKeycloakId(), context.targetType(), successfulTargetId);
            return result;
        }
        catch (RuntimeException ex)
        {
            String failedDetails = failureDetails.apply(ex);
            auditLogService.logAction(
                    context.adminKeycloakId(),
                    context.action(),
                    SystemLogOutcome.FAILURE,
                    context.targetType(),
                    context.targetId(),
                    failedDetails
            );
            log.warn("Admin action failed action={} actorId={} targetType={} targetId={} reason={}",
                    context.action(),
                    context.adminKeycloakId(),
                    context.targetType(),
                    context.targetId(),
                    summarizeException(ex));
            throw ex;
        }
    }

    public void runVoid(
            AuditContext context,
            Runnable operation,
            Supplier<@Nullable String> successDetails,
            Function<RuntimeException, @Nullable String> failureDetails
    )
    {
        run(
                context,
                () ->
                {
                    operation.run();
                    return context.targetId();
                },
                ignored -> context.targetId(),
                ignored -> successDetails.get(),
                failureDetails
        );
    }

    public record AuditContext(
            UUID adminKeycloakId,
            SystemLogAction action,
            String targetType,
            @Nullable String targetId
    )
    {
    }

    public static @NonNull String summarizeException(RuntimeException ex)
    {
        String message = ex.getMessage();
        if (message == null || message.isBlank())
        {
            return ex.getClass().getSimpleName();
        }
        return message;
    }

    public static @Nullable String detail(String label, @Nullable Object value)
    {
        if (value == null)
        {
            return null;
        }

        String normalized = value.toString().trim();
        if (normalized.isEmpty())
        {
            return null;
        }
        return label + "=" + normalized;
    }

    public static @Nullable String joinDetails(@Nullable String... details)
    {
        String joined = Arrays.stream(details)
                .filter(detail -> detail != null && !detail.isBlank())
                .collect(Collectors.joining(", "));

        return joined.isBlank() ? null : joined;
    }
}
