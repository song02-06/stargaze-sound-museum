package com.starmuseum.repo;

import com.starmuseum.domain.Exhibit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ExhibitRepository extends JpaRepository<Exhibit, Long> {

    List<Exhibit> findByStatusOrderByCreatedAtDesc(Exhibit.AuditStatus status);

    Optional<Exhibit> findByNo(String no);

    Optional<Exhibit> findFirstByOrderByNoDesc();

    long countByStatus(Exhibit.AuditStatus status);

    @Query("select e.id from Exhibit e where e.status = 'PASS'")
    List<Long> findPassedIds();

    List<Exhibit> findByKeeperIdOrderByCreatedAtDesc(Long keeperId);
}
