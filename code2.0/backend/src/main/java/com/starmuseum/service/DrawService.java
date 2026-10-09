package com.starmuseum.service;

import com.starmuseum.common.BizException;
import com.starmuseum.domain.DrawLog;
import com.starmuseum.domain.Exhibit;
import com.starmuseum.domain.HeritageSound;
import com.starmuseum.domain.Session;
import com.starmuseum.domain.TargetType;
import com.starmuseum.repo.DrawLogRepository;
import com.starmuseum.repo.ExhibitRepository;
import com.starmuseum.repo.HeritageSoundRepository;
import com.starmuseum.web.dto.Dtos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 打捞。
 *
 * 三条规则：
 * 1）**同一个池子**，不搞两套内容；
 * 2）**彩蛋保底** —— 新用户前 N 次必出一次历史之声。没有这条保底，
 *    多数人一辈子遇不到彩蛋，做了等于白做；
 * 3）**本轮不重复** —— 最近捞过的先排除，池子抽空了再放开。
 */
@Service
public class DrawService {

    private static final int AVOID_LOOKBACK = 12;

    private final ExhibitRepository exhibits;
    private final HeritageSoundRepository heritage;
    private final DrawLogRepository logs;
    private final ExhibitService exhibitService;
    private final HeritageService heritageService;
    private final double heritageRate;
    private final int guaranteeDraws;

    public DrawService(ExhibitRepository exhibits, HeritageSoundRepository heritage,
                       DrawLogRepository logs, ExhibitService exhibitService,
                       HeritageService heritageService,
                       @Value("${museum.draw.heritage-rate:0.12}") double heritageRate,
                       @Value("${museum.draw.heritage-guarantee-draws:3}") int guaranteeDraws) {
        this.exhibits = exhibits;
        this.heritage = heritage;
        this.logs = logs;
        this.exhibitService = exhibitService;
        this.heritageService = heritageService;
        this.heritageRate = heritageRate;
        this.guaranteeDraws = guaranteeDraws;
    }

    @Transactional
    public Dtos.DrawResult draw(Session session) {
        List<Exhibit> exhibitPool = exhibits.findByStatusOrderByCreatedAtDesc(Exhibit.AuditStatus.PASS);
        List<HeritageSound> heritagePool = heritage.findAllByOrderBySortOrderAsc();
        if (exhibitPool.isEmpty() && heritagePool.isEmpty()) {
            throw new BizException("馆里还没有可听的东西");
        }

        long totalDraws = logs.countBySessionId(session.getId());
        long heritageDraws = logs.countBySessionIdAndTargetType(session.getId(), TargetType.HERITAGE);

        boolean guaranteeActive = heritageDraws == 0 && totalDraws < guaranteeDraws;
        boolean hitHeritage = !heritagePool.isEmpty()
                && (guaranteeActive || ThreadLocalRandom.current().nextDouble() < heritageRate);

        if (hitHeritage && exhibitPool.isEmpty()) {
            hitHeritage = true;
        } else if (!hitHeritage && exhibitPool.isEmpty()) {
            hitHeritage = true;
        }

        List<Long> recent = recentIds(session.getId());

        if (hitHeritage) {
            HeritageSound chosen = pickPreferring(heritagePool, recent, HeritageSound::getId);
            logs.save(new DrawLog(session.getId(), TargetType.HERITAGE, chosen.getId()));
            return new Dtos.DrawResult("heritage", null, heritageService.toCard(chosen));
        }

        Exhibit chosen = pickPreferring(exhibitPool, recent, Exhibit::getId);
        logs.save(new DrawLog(session.getId(), TargetType.EXHIBIT, chosen.getId()));
        return new Dtos.DrawResult("exhibit", exhibitService.toCard(chosen), null);
    }

    private List<Long> recentIds(Long sessionId) {
        List<DrawLog> recent = logs.findBySessionIdOrderByCreatedAtDesc(sessionId);
        return recent.stream().limit(AVOID_LOOKBACK).map(DrawLog::getTargetId).toList();
    }

    /** 先在没有捞过的东西里挑；都捞过了就放开，别让用户捞不到 */
    private <T> T pickPreferring(List<T> pool, List<Long> recent, java.util.function.Function<T, Long> id) {
        List<T> fresh = new ArrayList<>(pool.size());
        for (T item : pool) {
            if (!recent.contains(id.apply(item))) {
                fresh.add(item);
            }
        }
        List<T> source = fresh.isEmpty() ? pool : fresh;
        return source.get(ThreadLocalRandom.current().nextInt(source.size()));
    }
}
