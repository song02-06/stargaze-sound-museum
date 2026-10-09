package com.starmuseum.audit;

import java.util.*;

/**
 * 自研 DFA 敏感词引擎：约 90 行、零依赖、可离线。
 *
 * 为什么不用现成词库：现成的（如 houbb/sensitive-word）词量大、准，
 * 但它是**依赖**，而这一路要做的是「快、可控、可解释」——
 * 答辩时能指着代码说清楚每一个命中是怎么来的。
 * 将来的升级路径是把这里换成生产级词库，接口不变。
 *
 * 归一化处理了三种最常见的绕过：全角字符、大小写、中间插空白。
 */
public class DfaSensitiveEngine {

    private static final class Node {
        final Map<Character, Node> next = new HashMap<>(2);
        boolean terminal;
    }

    private final Node root = new Node();
    private final List<String> whitelist = new ArrayList<>();
    private final int size;

    public DfaSensitiveEngine(Collection<String> words, Collection<String> whitelist) {
        int count = 0;
        for (String raw : words) {
            String word = normalize(raw);
            if (word.isEmpty()) {
                continue;
            }
            Node node = root;
            for (int i = 0; i < word.length(); i++) {
                node = node.next.computeIfAbsent(word.charAt(i), k -> new Node());
            }
            node.terminal = true;
            count++;
        }
        this.size = count;
        for (String w : whitelist) {
            String word = normalize(w);
            if (!word.isEmpty()) {
                this.whitelist.add(word);
            }
        }
    }

    public int size() {
        return size;
    }

    /** @return 命中的词，按出现顺序去重 */
    public List<String> findAll(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String t = normalize(text);
        Set<String> hits = new LinkedHashSet<>();

        for (int i = 0; i < t.length(); i++) {
            Node node = root;
            for (int j = i; j < t.length(); j++) {
                node = node.next.get(t.charAt(j));
                if (node == null) {
                    break;
                }
                if (node.terminal) {
                    hits.add(t.substring(i, j + 1));
                }
            }
        }

        applyWhitelist(t, hits);
        return new ArrayList<>(hits);
    }

    public boolean contains(String text) {
        return !findAll(text).isEmpty();
    }

    /**
     * 白名单用于消除「正常词含敏感子串」的误伤。
     * 例：敏感词「办证」，而用户写的是「办证明材料」——
     * 只要白名单里有「办证明」且文本里确实出现了它，就撤销这次命中。
     */
    private void applyWhitelist(String normalizedText, Set<String> hits) {
        for (String allow : whitelist) {
            if (normalizedText.contains(allow)) {
                hits.removeIf(allow::contains);
            }
        }
    }

    /** 全角转半角 + 去掉空白与标点 + 小写 */
    public static String normalize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0xFF01 && c <= 0xFF5E) {
                c = (char) (c - 0xFEE0);
            } else if (c == 0x3000) {
                continue;
            }
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                continue;
            }
            int type = Character.getType(c);
            if (type == Character.START_PUNCTUATION
                    || type == Character.END_PUNCTUATION
                    || type == Character.OTHER_PUNCTUATION
                    || type == Character.CONNECTOR_PUNCTUATION) {
                continue;
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}
