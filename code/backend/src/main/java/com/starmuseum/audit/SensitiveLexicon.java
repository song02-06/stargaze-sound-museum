package com.starmuseum.audit;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 两份词表，用途完全不同：
 *   deny-words.txt    → 命中直接拒
 *   review-only.txt   → 命中转人工复审（隐私类，不该机器一票否决）
 * 两份都受 whitelist.txt 保护，用来消除「正常词含敏感子串」的误伤。
 */
@Component
public class SensitiveLexicon {

    private final DfaSensitiveEngine deny;
    private final DfaSensitiveEngine reviewOnly;

    public SensitiveLexicon() {
        List<String> denyWords = read("sensitive/deny-words.txt");
        List<String> reviewWords = read("sensitive/review-only.txt");
        List<String> whitelist = read("sensitive/whitelist.txt");
        this.deny = new DfaSensitiveEngine(denyWords, whitelist);
        this.reviewOnly = new DfaSensitiveEngine(reviewWords, whitelist);
    }

    public List<String> findDeny(String text) {
        return deny.findAll(text);
    }

    public List<String> findReviewOnly(String text) {
        return reviewOnly.findAll(text);
    }

    public int denySize() {
        return deny.size();
    }

    public int reviewOnlySize() {
        return reviewOnly.size();
    }

    private static List<String> read(String classpath) {
        List<String> out = new ArrayList<>();
        ClassPathResource res = new ClassPathResource(classpath);
        if (!res.exists()) {
            return out;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.strip();
                if (!word.isEmpty() && !word.startsWith("#")) {
                    out.add(word);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("读取词表失败：" + classpath, e);
        }
        return out;
    }
}
