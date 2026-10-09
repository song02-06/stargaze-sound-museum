package com.yourname.museum.repository;

import com.yourname.museum.entity.VoiceBottle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VoiceBottleRepository extends JpaRepository<VoiceBottle, Long> {

    List<VoiceBottle> findTop20ByAuditStatusOrderByCreatedAtDesc(String auditStatus);

    List<VoiceBottle> findByAuditStatus(String auditStatus);

    long countByAuditStatus(String auditStatus);
}
