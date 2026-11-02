package com.starmuseum.repo;

import com.starmuseum.domain.HeritageSound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HeritageSoundRepository extends JpaRepository<HeritageSound, Long> {

    List<HeritageSound> findAllByOrderBySortOrderAsc();

    Optional<HeritageSound> findBySlug(String slug);
}
