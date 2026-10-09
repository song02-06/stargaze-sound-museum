package com.yourname.museum.common;

import com.github.houbb.sensitive.word.api.IWordAllow;
import com.github.houbb.sensitive.word.api.IWordDeny;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.github.houbb.sensitive.word.support.allow.WordAllows;
import com.github.houbb.sensitive.word.support.deny.WordDenys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认引擎：houbb/sensitive-word（Apache-2.0）。
 *
 * <p>相比自研 DFA，多出这些能力：内置约 6 万条词库、繁简互换、全半角互换、
 * 拼音与形近字等变体识别。这些正是实验二里「规则路覆盖变体弱」那条结论的反例，
 * 也是升级引擎的主要动机。
 *
 * <p><b>一个容易写错的地方</b>：{@code wordDeny(...)} 是「替换」而不是「追加」。
 * 如果直接传自己的词表，库自带的 6 万词就全丢了。必须用
 * {@code WordDenys.chains(WordDenys.defaults(), 自定义)} 包一层才对。
 * 白名单同理，用 {@code WordAllows.chains(...)}。
 */
@Component
@ConditionalOnProperty(name = "museum.audit.engine", havingValue = "houbb", matchIfMissing = true)
public class HoubbSensitiveWordEngine implements SensitiveWordEngine {

    private static final Logger log = LoggerFactory.getLogger(HoubbSensitiveWordEngine.class);

    private final SensitiveWordLexicon lexicon;
    private SensitiveWordBs sensitiveWordBs;

    public HoubbSensitiveWordEngine(SensitiveWordLexicon lexicon) {
        this.lexicon = lexicon;
    }

    @PostConstruct
    public void init() {
        IWordDeny customDeny = lexicon::detectionWords;
        IWordAllow customAllow = lexicon::allowWords;

        this.sensitiveWordBs = SensitiveWordBs.newInstance()
                .wordDeny(WordDenys.chains(WordDenys.defaults(), customDeny))
                .wordAllow(WordAllows.chains(WordAllows.defaults(), customAllow))
                .init();

        log.info("敏感词引擎 = houbb/sensitive-word（内置词库 + 自定义词表 + 白名单）");
    }

    @Override
    public List<String> match(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        return sensitiveWordBs.findAll(text);
    }

    @Override
    public int wordCount() {
        return lexicon.detectionWords().size();
    }

    @Override
    public String name() {
        return "houbb/sensitive-word";
    }
}
