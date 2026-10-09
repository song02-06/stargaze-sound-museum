package com.starmuseum.service;

import com.starmuseum.common.BizException;
import com.starmuseum.domain.Session;
import com.starmuseum.repo.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 轻身份。客户端生成并保管 deviceKey，服务端只认这个 key。
 * 没有邮箱、没有密码、没有头像 —— 这不是省事，是产品定义里的承诺。
 */
@Service
public class SessionService {

    private static final List<String> NICKNAME_POOL =
            List.of("拾荒", "临风", "南山", "阿柚", "小满", "夜班", "青禾", "行舟");

    private final SessionRepository sessions;

    public SessionService(SessionRepository sessions) {
        this.sessions = sessions;
    }

    @Transactional
    public Session resolve(String deviceKey, String nickname) {
        String key = deviceKey == null ? "" : deviceKey.strip();
        if (key.length() < 8 || key.length() > 64) {
            throw new BizException("设备标识不合法");
        }

        Session session = sessions.findByDeviceKey(key).orElse(null);
        if (session == null) {
            session = sessions.save(new Session(key, pickNickname()));
        }
        if (nickname != null && !nickname.isBlank()) {
            session.setNickname(cleanNickname(nickname));
        }
        session.touch();
        return sessions.save(session);
    }

    @Transactional
    public Session rename(Long sessionId, String nickname) {
        Session session = sessions.findById(sessionId)
                .orElseThrow(() -> new BizException("会话不存在"));
        session.setNickname(cleanNickname(nickname));
        session.touch();
        return sessions.save(session);
    }

    public Session require(Long sessionId) {
        if (sessionId == null) {
            throw new BizException("先建立会话");
        }
        return sessions.findById(sessionId)
                .orElseThrow(() -> new BizException("会话不存在，请重新建立"));
    }

    private static String pickNickname() {
        return NICKNAME_POOL.get(ThreadLocalRandom.current().nextInt(NICKNAME_POOL.size()));
    }

    private static String cleanNickname(String raw) {
        String n = raw.strip().replaceAll("\\s+", "");
        if (n.isEmpty() || n.length() > 12) {
            throw new BizException("昵称请控制在 1–12 个字");
        }
        return n;
    }
}
