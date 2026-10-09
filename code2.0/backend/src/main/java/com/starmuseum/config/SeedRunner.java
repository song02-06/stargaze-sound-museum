package com.starmuseum.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.starmuseum.common.TraceCodec;
import com.starmuseum.domain.Echo;
import com.starmuseum.domain.Exhibit;
import com.starmuseum.domain.HeritageSound;
import com.starmuseum.repo.EchoRepository;
import com.starmuseum.repo.ExhibitRepository;
import com.starmuseum.repo.HeritageSoundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 首次启动写入演示数据。
 *
 * 数据分两半，来源不同：
 *   - 文字（标题、展签、全文、回音）→ resources/seed/seed.json，手写的
 *   - 音频事实（时长、波形、文件）→ resources/seed/audio-manifest.json，由 tools/gen-audio.mjs 生成
 *
 * 两半都标了 seeded = true。上线前必须能一眼分辨哪些不是真用户留下的。
 */
@Component
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final ExhibitRepository exhibits;
    private final EchoRepository echoes;
    private final HeritageSoundRepository heritage;
    private final ObjectMapper mapper;

    public SeedRunner(ExhibitRepository exhibits, EchoRepository echoes,
                      HeritageSoundRepository heritage, ObjectMapper mapper) {
        this.exhibits = exhibits;
        this.echoes = echoes;
        this.heritage = heritage;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (exhibits.count() > 0 || heritage.count() > 0) {
            int fixed = normalizeSeedTimestamps();
            log.info("库里已有数据，跳过种子写入。{}", fixed > 0 ? "修正了 " + fixed + " 条演示数据的时间。" : "");
            return;
        }

        JsonNode seed = readJson("seed/seed.json");
        JsonNode manifest = readJson("seed/audio-manifest.json");
        if (seed == null) {
            log.warn("找不到 seed/seed.json，跳过种子写入。");
            return;
        }
        if (manifest == null) {
            log.warn("找不到 seed/audio-manifest.json —— 先跑 `node tools/gen-audio.mjs`。本次只写入文字，音频路径留空。");
        }

        Map<String, JsonNode> exhibitAudio = index(manifest, "exhibits");
        Map<String, JsonNode> heritageAudio = index(manifest, "heritage");
        Map<String, JsonNode> echoAudio = index(manifest, "echoes");

        int exhibitCount = 0;
        int echoCount = 0;

        for (JsonNode node : seed.path("exhibits")) {
            String id = node.path("id").asText();
            JsonNode audio = exhibitAudio.get(id);
            if (audio == null) {
                log.warn("展品 {} 没有对应音频，跳过。", id);
                continue;
            }

            Exhibit exhibit = new Exhibit(
                    node.path("no").asText(),
                    node.path("title").asText(),
                    node.path("sign").asText(),
                    node.path("full").asText(),
                    node.path("place").asText(),
                    LocalDate.parse(node.path("date").asText()),
                    "exhibits/" + id + ".wav",
                    audio.path("seconds").asInt(),
                    TraceCodec.encode(toList(audio.path("trace"))),
                    null,
                    node.path("keeper").asText(),
                    true);
            // 演示数据直接是「已通过」状态 —— 它们是内容，不是投稿
            exhibit.approve();
            // 入库时间回到录制当天，「最近入馆」才和日期一致
            exhibit.backdateTo(exhibit.getRecordedOn().atTime(12, 0));
            exhibit = exhibits.save(exhibit);
            exhibitCount++;

            long hoursAgo = 40;
            for (JsonNode echo : node.path("echoes")) {
                hoursAgo += 6;
                LocalDateTime at = LocalDateTime.now().minusHours(hoursAgo);
                boolean voice = "voice".equals(echo.path("kind").asText());

                String audioPath = null;
                Integer seconds = null;
                String trace = null;
                if (voice) {
                    JsonNode clip = echoAudio.get(echo.path("echoId").asText());
                    if (clip == null) {
                        continue;
                    }
                    audioPath = "echoes/" + echo.path("echoId").asText() + ".wav";
                    seconds = clip.path("seconds").asInt();
                    trace = TraceCodec.encode(toList(clip.path("trace")));
                }

                Echo entity = new Echo(exhibit.getId(), null, echo.path("author").asText(),
                        voice ? Echo.Kind.VOICE : Echo.Kind.TEXT,
                        echo.path("body").asText(null), audioPath, seconds, trace, true);
                // 演示回音是内容，不是投稿，直接给「已通过」，否则它们会在公开列表里消失
                entity.approve();
                entity.backdateTo(at);
                entity = echoes.save(entity);
                echoCount++;
            }
        }

        int heritageCount = 0;
        for (JsonNode node : seed.path("heritage")) {
            String id = node.path("id").asText();
            JsonNode audio = heritageAudio.get(id);
            if (audio == null) {
                log.warn("历史之声 {} 没有对应音频，跳过。", id);
                continue;
            }
            HeritageSound sound = new HeritageSound(
                    id,
                    node.path("archiveNo").asText(),
                    node.path("title").asText(),
                    node.path("era").asText(),
                    node.path("note").asText(),
                    node.path("source").asText(),
                    node.path("license").asText(),
                    "heritage/" + id + ".wav",
                    audio.path("seconds").asInt(),
                    TraceCodec.encode(toList(audio.path("trace"))),
                    node.path("weight").asInt(10),
                    node.path("sortOrder").asInt(0),
                    true);
            heritage.save(sound);
            heritageCount++;
        }

        log.info("种子写入完成：展品 {} 件、回音 {} 条、历史之声 {} 段。", exhibitCount, echoCount, heritageCount);
    }

    /**
     * 演示数据的自愈修正：入库时间必须等于录制当天的中午。
     *
     * 为什么要这一步：演示数据是内容，不是真的按时间投进来的。
     * 如果按种子文件的书写顺序排，"最近入馆"就会和日期对不上。
     * 只动 seeded = true 的记录，真实投稿永远不碰。
     */
    private int normalizeSeedTimestamps() {
        int fixed = 0;
        for (Exhibit e : exhibits.findAll()) {
            if (!e.isSeeded()) {
                continue;
            }
            LocalDateTime want = e.getRecordedOn().atTime(12, 0);
            if (!want.equals(e.getCreatedAt())) {
                e.backdateTo(want);
                exhibits.save(e);
                fixed++;
            }
        }
        return fixed;
    }

    private JsonNode readJson(String classpath) {
        ClassPathResource res = new ClassPathResource(classpath);
        if (!res.exists()) {
            return null;
        }
        try (InputStream in = res.getInputStream()) {
            return mapper.readTree(in);
        } catch (Exception e) {
            log.warn("读取 {} 失败：{}", classpath, e.getMessage());
            return null;
        }
    }

    private static Map<String, JsonNode> index(JsonNode manifest, String field) {
        Map<String, JsonNode> map = new HashMap<>();
        if (manifest == null) {
            return map;
        }
        for (JsonNode node : manifest.path(field)) {
            map.put(node.path("id").asText(), node);
        }
        return map;
    }

    private static List<Double> toList(JsonNode array) {
        List<Double> out = new ArrayList<>(array.size());
        for (JsonNode n : array) {
            out.add(n.asDouble());
        }
        return out;
    }
}
