package com.starmuseum.sign;

import java.util.*;

/**
 * 本地确定性压缩器 —— 零依赖、可离线、可复现。
 *
 * 做法：把全文切成句子，按「信息量」打分，选出高分句子，
 * 但**按原文顺序**拼回去。因为只是挑了原文的一部分句子，
 * 结果必然是原文的字符子序列 —— 结构上就不可能造假。
 *
 * 这不是「假装 AI」：它真的在做压缩，而且真的满足 SignGuard。
 * 接了真模型之后，模型输出同样要过 SignGuard，过不了就退回这里。
 */
public class MockSignCompressor implements SignCompressor {

    private static final String SENTENCE_SEPARATORS = "。！？；!?;\n";

    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public String compress(String fullText, String title, int maxChars) {
        if (fullText == null || fullText.isBlank()) {
            return "";
        }

        List<String> sentences = split(fullText);
        if (sentences.isEmpty()) {
            return clip(fullText, maxChars);
        }

        Set<String> entities = SignGuard.entities(fullText);

        record Candidate(int index, String text, int score) {
        }

        List<Candidate> ranked = new ArrayList<>(sentences.size());
        for (int i = 0; i < sentences.size(); i++) {
            String s = sentences.get(i);
            int score = score(s, entities, i, sentences.size());
            ranked.add(new Candidate(i, s, score));
        }
        ranked.sort(Comparator.comparingInt(Candidate::score).reversed()
                .thenComparingInt(Candidate::index));

        // 预算里要留一位给结尾的句号，否则会稳定超出一个字
        int budget = Math.max(1, maxChars - 1);
        List<Candidate> chosen = new ArrayList<>();
        int used = 0;
        for (Candidate c : ranked) {
            int cost = c.text().length() + (chosen.isEmpty() ? 0 : 1);
            if (used + cost <= budget) {
                chosen.add(c);
                used += cost;
            }
        }

        if (chosen.isEmpty()) {
            // 一句都放不下就退到前缀 —— 前缀永远是子序列
            return clip(fullText, maxChars);
        }

        // 关键一步：按原文顺序还原，保证结果仍然是子序列
        chosen.sort(Comparator.comparingInt(Candidate::index));
        StringBuilder sb = new StringBuilder();
        for (Candidate c : chosen) {
            if (sb.length() > 0) {
                sb.append('。');
            }
            sb.append(c.text());
        }
        sb.append('。');

        String result = sb.toString();
        if (result.length() > maxChars) {
            result = clip(result, maxChars);
        }
        // 自己先过一遍守卫；过不了就退回最保守的前缀
        return SignGuard.check(fullText, result).ok() ? result : clip(fullText, maxChars);
    }

    private static int score(String sentence, Set<String> entities, int index, int total) {
        int score = 0;
        // 含时间、数字、引语的句子信息量最高，优先保留
        for (String e : entities) {
            if (sentence.contains(e)) {
                score += 4;
                break;
            }
        }
        if (sentence.matches(".*(在|从|到|去|回|那年|当时|后来|那天).*")) {
            score += 2;
        }
        if (sentence.length() >= 10 && sentence.length() <= 40) {
            score += 2;
        }
        if (sentence.length() > 40) {
            score -= 1;
        }
        // 首句通常是交代，末句通常是落点，各加一点
        if (index == 0) {
            score += 1;
        }
        if (index == total - 1 && total > 1) {
            score += 1;
        }
        return score;
    }

    static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (SENTENCE_SEPARATORS.indexOf(c) >= 0) {
                addIfNotBlank(out, sb);
            } else {
                sb.append(c);
            }
        }
        addIfNotBlank(out, sb);
        return out;
    }

    private static void addIfNotBlank(List<String> out, StringBuilder sb) {
        String s = sb.toString().trim();
        if (!s.isEmpty()) {
            out.add(s);
        }
        sb.setLength(0);
    }

    static String clip(String text, int maxChars) {
        String clean = text.strip();
        return clean.length() <= maxChars ? clean : clean.substring(0, maxChars);
    }
}
