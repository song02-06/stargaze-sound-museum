package com.yourname.museum.repository;

import com.yourname.museum.entity.HeritageSound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HeritageSoundRepository extends JpaRepository<HeritageSound, Long> {

    List<HeritageSound> findByEnabledTrue();
}
