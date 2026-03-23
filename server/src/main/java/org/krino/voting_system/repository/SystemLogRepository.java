package org.krino.voting_system.repository;

import org.krino.voting_system.entity.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemLogRepository extends JpaRepository<SystemLog, Long>
{
}
