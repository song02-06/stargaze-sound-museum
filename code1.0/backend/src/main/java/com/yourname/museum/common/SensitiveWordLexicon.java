package com.yourname.museum.common;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 词表加载器 —— 对应实施手册 4.1 的「补充词表 + 白名单」。
 *
 * <p>四份词表各司其职：
 * <ul>
 *   <li>{@code deny-words.txt}     拦截词，命中直接拒绝；</li>
 *   <li>{@code extra-words.txt}    按测试集反馈持续补充的词；</li>
 *   <li>{@code review-only.txt}    隐私类词，命中后转人工复审而不是拒绝；</li>
 *   <li>{@code whitelist.txt}      白名单，用于消除「正常词含敏感子串」的误伤。</li>
 * </ul>
 *
 * <p>注意「是否检测」与「如何处置」是两件事：review-only 的词同样要参与检测，
 * 只是处置结果不同。把它们也放进检测词表，就不会漏检。
 */
@Component
public class SensitiveWordLexicon {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordLexicon.class);
    private static final String BASE = "sensitive/";

    private final List<String> denyWords = new ArrayList<>();
    private final List<String> reviewOnlyWords = new ArrayList<>();
    private final List<String> whitelist = new ArrayList<>();

    @PostConstruct
    public void load() {
        denyWords.addAll(readLines("deny-words.txt"));
        denyWords.addAll(readLines("extra-words.txt"));
        reviewOnlyWords.addAll(readLines("review-only.txt"));
        whitelist.addAll(readLines("whitelist.txt"));

        log.info("敏感词词表已加载：拦截 {} 条 + 隐私复审 {} 条 + 白名单 {} 条（不含引擎内置词库）",
                denyWords.size(), reviewOnlyWords.size(), whitelist.size());
    }

    /** 参与检测的全部词 = 拦截词 + 隐私复审词。 */
    public List<String> detectionWords() {
        List<String> all = new ArrayList<>(denyWords.size() + reviewOnlyWords.size());
        all.addAll(denyWords);
        all.addAll(reviewOnlyWords);
        return all;
    }

    public List<String> allowWords() {
        return new ArrayList<>(whitelist);
    }

    /** 该词命中后应该走人工复审，而不是直接拒绝。 */
    public boolean isReviewOnly(String word) {
        return reviewOnlyWords.contains(word);
    }

    public int denyCount() {
        return denyWords.size();
    }

    public int reviewOnlyCount() {
        return reviewOnlyWords.size();
    }

    private List<String> readLines(String fileName) {
        ClassPathResource resource = new ClassPathResource(BASE + fileName);
        if (!resource.exists()) {
            log.warn("词表不存在，跳过：{}", BASE + fileName);
            return List.of();
        }
        Set<String> lines = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                if (word.isEmpty() || word.startsWith("#") || lines.contains(word)) {
                    continue;
                }
                lines.add(word);
            }
        } catch (IOException e) {
            log.error("读取词表失败：{}", fileName, e);
        }
        return new ArrayList<>(lines);
    }
}
