package com.starmuseum.repo;

import com.starmuseum.domain.DrawLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DrawLogRepository extends JpaRepository<DrawLog, Long> {

    List<DrawLog> findBySessionIdOrderByCreatedAtDesc(Long sessionId);

    long countBySessionId(Long sessionId);

    long countBySessionIdAndTargetType(Long sessionId, com.starmuseum.domain.TargetType targetType);
}
