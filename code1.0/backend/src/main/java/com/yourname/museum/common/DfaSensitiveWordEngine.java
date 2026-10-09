package com.yourname.museum.common;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 备用引擎：自研 DFA（确定有限自动机），约 80 行、零依赖、可离线。
 *
 * <p>保留它的理由不是「比库更好」，而是三个很实际的场景：
 * <ol>
 *   <li>演示现场没有网络、或依赖装不上时的兜底；</li>
 *   <li>做实验二时作为「纯规则、无变体识别」的对照组；</li>
 *   <li>答辩讲 DFA 原理时，代码是自己写的一眼能讲清。</li>
 * </ol>
 *
 * <p>它只做精确匹配，不处理繁简、全半角、拼音变体 —— 这是它和 houbb 版的核心差距。
 */
@Component
@ConditionalOnProperty(name = "museum.audit.engine", havingValue = "dfa")
public class DfaSensitiveWordEngine implements SensitiveWordEngine {

    private static final Logger log = LoggerFactory.getLogger(DfaSensitiveWordEngine.class);

    /** DFA 节点：children 是「下一个字 → 子节点」，end 表示走到这里能拼出一个完整词。 */
    private static final class Node {
        private final Map<Character, Node> children = new HashMap<>();
        private boolean end;
    }

    private final SensitiveWordLexicon lexicon;
    private final Node root = new Node();
    private int wordCount;

    public DfaSensitiveWordEngine(SensitiveWordLexicon lexicon) {
        this.lexicon = lexicon;
    }

    @PostConstruct
    public void init() {
        List<String> words = lexicon.detectionWords();
        for (String word : words) {
            addWord(word);
        }
        this.wordCount = words.size();
        log.info("敏感词引擎 = 自研 DFA（零依赖，精确匹配，不识别变体），词条 {} 条", wordCount);
    }

    private void addWord(String word) {
        Node node = root;
        for (char c : word.toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new Node());
        }
        node.end = true;
    }

    @Override
    public List<String> match(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        Set<String> hits = new LinkedHashSet<>();
        String lower = text.toLowerCase();
        char[] chars = lower.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            Node node = root;
            for (int j = i; j < chars.length; j++) {
                Node next = node.children.get(chars[j]);
                if (next == null) {
                    break;
                }
                node = next;
                if (node.end) {
                    hits.add(text.substring(i, j + 1));
                }
            }
        }
        return new ArrayList<>(hits);
    }

    @Override
    public int wordCount() {
        return wordCount;
    }

    @Override
    public String name() {
        return "自研DFA";
    }
}
