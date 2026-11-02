package com.starmuseum.repo;

import com.starmuseum.domain.Notification;
import com.starmuseum.domain.TargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 收件箱一次看完，最新的在上。课程项目的数据量不需要分页参数 */
    List<Notification> findByRecipientSessionIdOrderByCreatedAtDesc(Long recipientSessionId);

    List<Notification> findByRecipientSessionIdAndTargetGoneFalseOrderByCreatedAtDesc(
            Long recipientSessionId);

    long countByRecipientSessionIdAndReadAtIsNullAndTargetGoneFalse(Long recipientSessionId);

    /** 标记已读时**必须**带上接收人：别人的消息不能被我标成已读 */
    Optional<Notification> findByIdAndRecipientSessionId(Long id, Long recipientSessionId);

    /** 展品被删了，指向它的消息就不能再留一个必然点不开的入口 */
    List<Notification> findByTargetTypeAndTargetId(TargetType targetType, Long targetId);
}
