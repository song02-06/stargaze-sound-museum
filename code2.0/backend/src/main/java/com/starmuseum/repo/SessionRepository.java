package com.starmuseum.repo;

import com.starmuseum.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {
    Optional<Session> findByDeviceKey(String deviceKey);
}
