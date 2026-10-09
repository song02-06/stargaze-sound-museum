package com.yourname.museum.audit;

import com.yourname.museum.common.HoubbSensitiveWordEngine;
import com.yourname.museum.common.SensitiveWordEngine;
import com.yourname.museum.common.SensitiveWordLexicon;
import com.yourname.museum.repository.AuditRecordRepository;
import com.yourname.museum.service.AuditService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * 阶段 4 验收：构造 10 条测试文本（违规 5 / 正常 5），判定全部符合预期。
 *
 * <p>这里刻意直接复用 {@link AuditService#classifyRule}，而不是在测试里重写一遍判定规则 ——
 * 否则测试通过只能说明「测试自己的规则写对了」，说明不了线上行为。
 */
class RuleAuditAcceptanceTest {

    private static AuditService auditService;

    @BeforeAll
    static void setUp() {
        SensitiveWordLexicon lexicon = new SensitiveWordLexicon();
        lexicon.load();

        SensitiveWordEngine engine = new HoubbSensitiveWordEngine(lexicon);
        ((HoubbSensitiveWordEngine) engine).init();

        // ruleStage 会写审核流水，所以仓储必须给个 mock。
        // （第一版这里传了 null，13 个用例全挂在 NPE 上——这也说明测试确实覆盖到了真实路径。）
        auditService = new AuditService(engine, lexicon, mock(AuditRecordRepository.class));
    }

    record Case(String text, String expected, String note) {
        @Override
        public String toString() {
            return expected + " ← " + text;
        }
    }

    static Stream<Case> 十条验收样本() {
        return Stream.of(
                // —— 违规 5 条，期望 REJECT ——
                new Case("加微信 兼职日结 扫码进群", "REJECT", "广告导流"),
                new Case("这里有博彩网站，点进去就能玩", "REJECT", "违法违规"),
                new Case("你这个人真是傻逼", "REJECT", "攻击辱骂"),
                new Case("可以代开发票，需要的联系", "REJECT", "违法经营"),
                new Case("办证刻章一条龙服务", "REJECT", "违法经营"),

                // —— 正常 5 条，期望 PASS ——
                new Case("今天路过以前的小学，门口那棵树还在，就是比以前高了很多。", "PASS", "普通留言"),
                new Case("我想对三年前的自己说，别怕，你后来真的做到了。", "PASS", "普通留言"),
                new Case("外婆家的老式挂钟每到整点就会响，现在那个声音只能在记忆里找了。", "PASS", "普通留言"),
                new Case("我要去办证明，需要带什么材料？", "PASS", "白名单：「办证明」不应被「办证」误伤"),
                new Case("毕业那天我们都没哭，就是一直说以后要常联系。", "PASS", "普通留言")
        );
    }

    @ParameterizedTest(name = "[{index}] {2} —— {0}")
    @MethodSource("十条验收样本")
    void 十条测试文本判定符合预期(Case c) {
        List<String> hits = auditService.ruleStage(c.text(), null);
        assertEquals(c.expected(), auditService.classifyRule(hits),
                () -> "文本「" + c.text() + "」命中=" + hits);
    }

    @Test
    @DisplayName("隐私提醒类命中后转人工复审，而不是直接拒绝")
    void 隐私词应转人工复审() {
        String text = "我的手机号码换了你存一下";
        List<String> hits = auditService.ruleStage(text, null);
        assertEquals("REVIEW", auditService.classifyRule(hits), () -> "命中=" + hits);
    }

    @Test
    @DisplayName("白名单优先级高于拦截词：办证明 正常，办证 拦截")
    void 白名单应消除误伤() {
        assertEquals("PASS", auditService.classifyRule(auditService.ruleStage("办证明", null)));
        assertEquals("REJECT", auditService.classifyRule(auditService.ruleStage("办证", null)));
    }

    @Test
    @DisplayName("引擎应报告当前生效的自定义词条数")
    void 词条数应大于零() {
        assertEquals(true, auditService.wordCount() > 0);
    }
}
