package org.krino.voting_system.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.krino.voting_system.entity.SystemLog;
import org.krino.voting_system.entity.enums.SystemLogAction;
import org.krino.voting_system.entity.enums.SystemLogOutcome;
import org.krino.voting_system.repository.CitizenRepository;
import org.krino.voting_system.repository.SystemLogRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService
{
    private static final int TARGET_ID_MAX_LENGTH = 128;
    private static final int TARGET_TYPE_MAX_LENGTH = 64;

    private final SystemLogRepository systemLogRepository;
    private final CitizenRepository citizenRepository;

    public void logAction(
            @Nullable UUID adminKeycloakId,
            SystemLogAction action,
            SystemLogOutcome outcome,
            String targetType,
            @Nullable String targetId,
            @Nullable String details
    )
    {
        try
        {
            SystemLog systemLog = new SystemLog();
            if (adminKeycloakId != null)
            {
                citizenRepository.findByKeycloakIdAndIsDeletedFalse(adminKeycloakId)
                        .ifPresent(systemLog::setAdmin);
            }

            systemLog.setAction(action.name());
            systemLog.setOutcome(outcome);
            systemLog.setTargetType(truncateToNull(targetType, TARGET_TYPE_MAX_LENGTH));
            systemLog.setTargetId(truncateToNull(targetId, TARGET_ID_MAX_LENGTH));
            systemLog.setDetails(blankToNull(details));

            systemLogRepository.save(systemLog);
        }
        catch (RuntimeException ex)
        {
            log.error("Failed to persist audit log action={} outcome={} targetType={} targetId={}",
                    action, outcome, targetType, targetId, ex);
        }
    }

    private static @Nullable String truncateToNull(@Nullable String value, int maxLength)
    {
        String normalized = blankToNull(value);
        if (normalized == null)
        {
            return null;
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static @Nullable String blankToNull(@Nullable String value)
    {
        if (value == null)
        {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
