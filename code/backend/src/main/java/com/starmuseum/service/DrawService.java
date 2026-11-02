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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * 打捞。
 *
 * 三条规则：
 * 1）**同一个池子**，不搞两套内容；
 * 2）**彩蛋保底** —— 新用户前 N 次必出一次历史之声。没有这条保底，
 *    多数人一辈子遇不到彩蛋，做了等于白做；
 * 3）**本轮不重复** —— 最近捞过的先排除，池子抽空了再放开。
 *
 * ⚠️ 去重必须**分类型**：exhibit 和 heritage 的自增 id 都从 1 开始，
 * 只比数字会把「历史之声 1 号」和「用户展品 1 号」当成同一个东西 ——
 * 捞过其中一个，另一个就再也抽不到。用 TargetType:ID 组成的键做比较。
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

        // 先跨类型排除近期目标，再选择内容类型。
        List<Exhibit> others = exhibitPool.stream()
                .filter(e -> !session.getId().equals(e.getKeeperId())).toList();
        if (!others.isEmpty() || !heritagePool.isEmpty()) exhibitPool = others;
        Set<String> recent = recentKeys(session.getId());
        List<Exhibit> freshExhibits = exhibitPool.stream()
                .filter(e -> !recent.contains(key(TargetType.EXHIBIT, e.getId()))).toList();
        List<HeritageSound> freshHeritage = heritagePool.stream()
                .filter(h -> !recent.contains(key(TargetType.HERITAGE, h.getId()))).toList();
        if (!freshExhibits.isEmpty() || !freshHeritage.isEmpty()) {
            exhibitPool = freshExhibits;
            heritagePool = freshHeritage;
        } else {
            List<DrawLog> previous = logs.findBySessionIdOrderByCreatedAtDesc(session.getId());
            if (!previous.isEmpty() && exhibitPool.size() + heritagePool.size() > 1) {
                DrawLog last = previous.get(0);
                exhibitPool = exhibitPool.stream().filter(e -> last.getTargetType() != TargetType.EXHIBIT
                        || !e.getId().equals(last.getTargetId())).toList();
                heritagePool = heritagePool.stream().filter(h -> last.getTargetType() != TargetType.HERITAGE
                        || !h.getId().equals(last.getTargetId())).toList();
            }
        }
        boolean guaranteeActive = heritageDraws == 0 && totalDraws >= guaranteeDraws - 1;
        boolean firstOrdinary = totalDraws == 0 && !exhibitPool.isEmpty();
        boolean hitHeritage = !heritagePool.isEmpty() && !firstOrdinary
                && (exhibitPool.isEmpty() || guaranteeActive
                || ThreadLocalRandom.current().nextDouble() < heritageRate);

        if (hitHeritage) {
            HeritageSound chosen = pickPreferring(heritagePool, recent, TargetType.HERITAGE,
                    HeritageSound::getId);
            logs.save(new DrawLog(session.getId(), TargetType.HERITAGE, chosen.getId()));
            return new Dtos.DrawResult("heritage", null, heritageService.toCard(chosen));
        }

        Exhibit chosen = pickPreferring(exhibitPool, recent, TargetType.EXHIBIT, Exhibit::getId);
        logs.save(new DrawLog(session.getId(), TargetType.EXHIBIT, chosen.getId()));
        return new Dtos.DrawResult("exhibit", exhibitService.toCard(chosen), null);
    }

    /** 最近捞过的对象，用「类型:编号」表示 —— 不同类型同编号是两个对象 */
    private Set<String> recentKeys(Long sessionId) {
        List<DrawLog> recent = logs.findBySessionIdOrderByCreatedAtDesc(sessionId);
        Set<String> keys = new HashSet<>();
        for (DrawLog log : recent) {
            if (keys.size() >= AVOID_LOOKBACK) {
                break;
            }
            keys.add(key(log.getTargetType(), log.getTargetId()));
        }
        return keys;
    }

    static String key(TargetType type, Long id) {
        return type.name() + ":" + id;
    }

    /** 先在没有捞过的东西里挑；都捞过了就放开，别让用户捞不到 */
    private <T> T pickPreferring(List<T> pool, Set<String> recent, TargetType type,
                                 Function<T, Long> id) {
        List<T> source = freshItems(pool, recent, type, id);
        return source.get(ThreadLocalRandom.current().nextInt(source.size()));
    }

    /** 抽出还没捞过的那部分；全捞过了就退回整个池子 */
    List<Exhibit> freshExhibits(List<Exhibit> pool, Set<String> recent) {
        return freshItems(pool, recent, TargetType.EXHIBIT, Exhibit::getId);
    }

    private <T> List<T> freshItems(List<T> pool, Set<String> recent, TargetType type,
                                   Function<T, Long> id) {
        List<T> fresh = new ArrayList<>(pool.size());
        for (T item : pool) {
            if (!recent.contains(key(type, id.apply(item)))) {
                fresh.add(item);
            }
        }
        return fresh.isEmpty() ? pool : fresh;
    }
}
