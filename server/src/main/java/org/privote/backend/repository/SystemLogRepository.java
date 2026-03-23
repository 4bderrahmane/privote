package org.privote.backend.repository;

import org.privote.backend.entity.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemLogRepository extends JpaRepository<SystemLog, Long>
{
}
