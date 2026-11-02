package com.starmuseum.service;

import com.starmuseum.common.BizException;
import com.starmuseum.domain.Session;
import com.starmuseum.repo.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;

@Service
public class AccountService {
    private final SessionRepository sessions;
    private final SessionService guests;
    public AccountService(SessionRepository sessions, SessionService guests) {
        this.sessions = sessions;
        this.guests = guests;
    }

    @Transactional
    public Session register(String rawUsername, String password, String deviceKey) {
        String username = username(rawUsername);
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new BizException("密码请使用 8–128 个字符");
        }
        if (sessions.findByUsername(username).isPresent()) throw new BizException("用户名已被使用");
        Session guest = guests.resolve(deviceKey, null);
        if (guest.getUsername() != null) throw new BizException("当前身份已经注册");
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String encoded = Base64.getEncoder().encodeToString(salt) + ":"
                + Base64.getEncoder().encodeToString(derive(password, salt));
        guest.register(username, encoded);
        try { return sessions.saveAndFlush(guest); }
        catch (DataIntegrityViolationException e) { throw new BizException("用户名已被使用，请换一个"); }
    }

    public Session login(String rawUsername, String password) {
        String username = username(rawUsername);
        if (password == null || password.length() > 128) throw new BizException("用户名或密码不正确");
        Session account = sessions.findByUsername(username).orElseThrow(() -> new BizException("用户名或密码不正确"));
        String[] parts = account.getPasswordHash().split(":");
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] expected = Base64.getDecoder().decode(parts[1]);
        if (!MessageDigest.isEqual(expected, derive(password, salt))) throw new BizException("用户名或密码不正确");
        return account;
    }

    private static String username(String raw) {
        String name = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_]{3,24}")) throw new BizException("用户名请使用 3–24 位字母、数字或下划线");
        return name;
    }

    private static byte[] derive(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 600_000, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
        finally { spec.clearPassword(); }
    }
}
