package com.yourname.museum.repository;

import com.yourname.museum.entity.AuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {

    List<AuditRecord> findTop50ByOrderByCreatedAtDesc();

    List<AuditRecord> findByTargetTypeAndTargetId(String targetType, Long targetId);
}
