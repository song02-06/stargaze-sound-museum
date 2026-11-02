package com.starmuseum.repo;

import com.starmuseum.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {
    Optional<Session> findByUsername(String username);

    Optional<Session> findByDeviceKey(String deviceKey);
}
