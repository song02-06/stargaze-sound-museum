package com.starmuseum.repo;

import com.starmuseum.domain.Echo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EchoRepository extends JpaRepository<Echo, Long> {

    List<Echo> findByExhibitIdAndStatusOrderByCreatedAtDesc(Long exhibitId, Echo.AuditStatus status);

    List<Echo> findByStatusOrderByCreatedAtAsc(Echo.AuditStatus status);

    long countByExhibitIdAndStatus(Long exhibitId, Echo.AuditStatus status);

    List<Echo> findByAuthorIdOrderByCreatedAtDesc(Long authorId);

    long countByStatus(Echo.AuditStatus status);

    /** 展品没了，挂在它下面的回音也留不住 */
    void deleteByExhibitId(Long exhibitId);
}
