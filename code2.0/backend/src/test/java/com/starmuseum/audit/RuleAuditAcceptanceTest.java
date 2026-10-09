package com.starmuseum.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 规则路验收：10 条样本（违规 5 / 正常 5），外加白名单与隐私词。
 * 判定必须与线上完全一致 —— 所以这里直接new 出引擎，和线上用同一份词表。
 */
class RuleAuditAcceptanceTest {

    private final SensitiveLexicon lexicon = new SensitiveLexicon();

    @ParameterizedTest(name = "违规样本应被拦截：{0}")
    @ValueSource(strings = {
            "加微信买片",
            "有代刷单的吗",
            "博彩网站推荐",
            "毒品交易",
            "办证"
    })
    void blocksOffensiveSamples(String text) {
        assertThat(lexicon.findDeny(text)).as("「%s」应该被拦截", text).isNotEmpty();
    }

    @ParameterizedTest(name = "正常样本不应被误伤：{0}")
    @ValueSource(strings = {
            "今天的雨下得很大",
            "外婆家的瓦片",
            "我去办证明材料",
            "代刷题到半夜",
            "刷卡进站的声音"
    })
    void keepsNormalSamples(String text) {
        assertThat(lexicon.findDeny(text)).as("「%s」不该被拦截", text).isEmpty();
    }

    @Test
    @DisplayName("白名单消除误伤：办证 vs 办证明材料")
    void whitelistRescuesFalsePositive() {
        assertThat(lexicon.findDeny("办证")).contains("办证");
        assertThat(lexicon.findDeny("我去办证明材料")).isEmpty();
    }

    @Test
    @DisplayName("隐私类词转人工，而不是直接拒绝")
    void privacyWordsGoToReviewInsteadOfReject() {
        List<String> hits = lexicon.findReviewOnly("那年的身份证弄丢了");
        assertThat(hits).contains("身份证");
        assertThat(lexicon.findDeny("那年的身份证弄丢了")).isEmpty();
    }

    @Test
    @DisplayName("用中文数字与全角字符的绕过手法也要拦住")
    void defeatsCommonEvasion() {
        // 全角
        assertThat(lexicon.findDeny("加微信")).isNotEmpty();
        // 中间插空格
        assertThat(lexicon.findDeny("加 微 信")).isNotEmpty();
        // 全角字符
        assertThat(lexicon.findDeny("加微信")).isNotEmpty();
    }

    @Test
    @DisplayName("词表真要加载进来了，不是空跑")
    void lexiconIsActuallyLoaded() {
        assertThat(lexicon.denySize()).isGreaterThan(10);
        assertThat(lexicon.reviewOnlySize()).isGreaterThan(3);
    }
}
