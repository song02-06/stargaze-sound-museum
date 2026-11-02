package com.starmuseum.service;

import com.starmuseum.common.BizException;
import com.starmuseum.domain.HeritageSound;
import com.starmuseum.repo.HeritageSoundRepository;
import com.starmuseum.web.dto.Dtos;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HeritageService {

    private final HeritageSoundRepository repository;

    public HeritageService(HeritageSoundRepository repository) {
        this.repository = repository;
    }

    public List<Dtos.HeritageCard> listAll() {
        return repository.findAllByOrderBySortOrderAsc().stream().map(this::toCard).toList();
    }

    public HeritageSound require(String idOrSlug) {
        return repository.findBySlug(idOrSlug)
                .or(() -> parseId(idOrSlug).flatMap(repository::findById))
                .orElseThrow(() -> new BizException("专区里没有这一段"));
    }

    public Dtos.HeritageCard toCard(HeritageSound h) {
        return new Dtos.HeritageCard(
                h.getId(), h.getSlug(), h.getArchiveNo(), h.getTitle(), h.getEra(), h.getNote(),
                h.getSource(), h.getLicense(), h.getSeconds(),
                com.starmuseum.common.TraceCodec.decode(h.getTrace()),
                "/audio/" + h.getAudioPath());
    }

    private static java.util.Optional<Long> parseId(String raw) {
        try {
            return java.util.Optional.of(Long.parseLong(raw));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }
}
