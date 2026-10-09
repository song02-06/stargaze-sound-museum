package com.yourname.museum.service;

import com.yourname.museum.common.BizException;
import com.yourname.museum.dto.DrawItem;
import com.yourname.museum.entity.HeritageSound;
import com.yourname.museum.entity.VoiceBottle;
import com.yourname.museum.repository.HeritageSoundRepository;
import com.yourname.museum.repository.VoiceBottleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * 随机捞取。两件事必须做对：
 * 1）普通瓶与历史彩蛋按权重抽取（默认 7:3）；
 * 2）本轮不重复 —— 记录已抽过的 id，避免答辩演示时反复抽到同一条。
 */
@Service
public class DrawService {

    private final VoiceBottleRepository bottleRepository;
    private final HeritageSoundRepository heritageRepository;
    private final Random random = new Random();

    private final Deque<Long> recentBottles = new ArrayDeque<>();
    private final Deque<Long> recentHeritage = new ArrayDeque<>();

    @Value("${museum.draw.bottle-weight:70}")
    private int bottleWeight;

    @Value("${museum.draw.heritage-weight:30}")
    private int heritageWeight;

    @Value("${museum.draw.recent-limit:20}")
    private int recentLimit;

    public DrawService(VoiceBottleRepository bottleRepository, HeritageSoundRepository heritageRepository) {
        this.bottleRepository = bottleRepository;
        this.heritageRepository = heritageRepository;
    }

    public synchronized DrawItem draw() {
        List<VoiceBottle> bottles = bottleRepository.findByAuditStatus("PASS");
        List<HeritageSound> heritage = heritageRepository.findByEnabledTrue();

        boolean hasBottle = !bottles.isEmpty();
        boolean hasHeritage = !heritage.isEmpty();
        if (!hasBottle && !hasHeritage) {
            throw new BizException(4007, "星空里还没有声音，先去留一段吧");
        }

        boolean wantHeritage;
        if (!hasBottle) {
            wantHeritage = true;
        } else if (!hasHeritage) {
            wantHeritage = false;
        } else {
            int total = bottleWeight + heritageWeight;
            wantHeritage = random.nextInt(total) < heritageWeight;
        }

        return wantHeritage ? drawHeritage(heritage) : drawBottle(bottles);
    }

    private DrawItem drawBottle(List<VoiceBottle> all) {
        VoiceBottle picked = pickWithNoRepeat(all, recentBottles, VoiceBottle::getId);
        if (picked == null) {
            throw new BizException(4007, "星空里还没有声音，先去留一段吧");
        }
        return new DrawItem(
                "BOTTLE", picked.getId(), picked.getTitle(), picked.getPolishedText(),
                picked.getTags(), picked.getEmotion(),
                "/audio/" + picked.getAudioPath(), picked.getDurationMs(),
                null, null, null, null, null, null);
    }

    private DrawItem drawHeritage(List<HeritageSound> all) {
        HeritageSound picked = pickWithNoRepeat(all, recentHeritage, HeritageSound::getId);
        if (picked == null) {
            // 彩蛋抽完了就回退到普通瓶，保证流程不中断
            List<VoiceBottle> bottles = bottleRepository.findByAuditStatus("PASS");
            if (bottles.isEmpty()) {
                throw new BizException(4007, "星空里还没有声音，先去留一段吧");
            }
            return drawBottle(bottles);
        }
        return new DrawItem(
                "HERITAGE", picked.getId(), picked.getTitle(), picked.getStory(),
                null, null,
                "/audio/" + picked.getAudioPath(), null,
                picked.getSpeaker(), picked.getEra(), picked.getCountry(),
                picked.getSourceName(), picked.getSourceUrl(), picked.getLicenseNote());
    }

    private <T> T pickWithNoRepeat(List<T> all, Deque<Long> recent, java.util.function.Function<T, Long> idOf) {
        List<T> fresh = all.stream().filter(item -> !recent.contains(idOf.apply(item))).toList();
        List<T> pool = fresh.isEmpty() ? all : fresh;
        if (pool.isEmpty()) {
            return null;
        }
        T picked = pool.get(random.nextInt(pool.size()));
        remember(recent, idOf.apply(picked));
        return picked;
    }

    private void remember(Deque<Long> recent, Long id) {
        if (recent.contains(id)) {
            recent.remove(id);
        }
        recent.addFirst(id);
        while (recent.size() > recentLimit) {
            recent.removeLast();
        }
    }

    /** 给管理后台看当前热度。 */
    public String stats() {
        return "bottleCandidates=" + bottleRepository.countByAuditStatus("PASS")
                + ", heritageCandidates=" + heritageRepository.findByEnabledTrue().size()
                + ", recentBottles=" + recentBottles.size()
                + ", recentHeritage=" + recentHeritage.size();
    }
}
