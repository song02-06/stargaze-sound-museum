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

    /**
     * 这件展品下面的**全部**回音，不分状态。
     * 删除展品时必须用它：待审、被驳回的语音回音也已经落盘，
     * 只查 PASS 会把文件留在磁盘上。
     */
    List<Echo> findByExhibitId(Long exhibitId);

    /** 展品没了，挂在它下面的回音也留不住 */
    void deleteByExhibitId(Long exhibitId);
}
