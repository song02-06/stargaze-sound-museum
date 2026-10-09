package com.starmuseum.repo;

import com.starmuseum.domain.AuditRecord;
import com.starmuseum.domain.TargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {

    List<AuditRecord> findByTargetTypeAndTargetIdOrderByCreatedAtAsc(TargetType type, Long targetId);

    List<AuditRecord> findTop200ByOrderByCreatedAtDesc();
}
