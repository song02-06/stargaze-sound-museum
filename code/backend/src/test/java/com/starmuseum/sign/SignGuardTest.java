package com.starmuseum.sign;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「AI 只做减法」的契约测试。
 *
 * 产品定义里承诺过：展签上的每一个字都能在作者原话里找到出处。
 * 这个文件就是那句承诺的可执行版本 —— 一旦有人放开约束（比如允许 AI 扩写），
 * 这里会立刻变红，而不是等到用户发现展签在编故事。
 */
class SignGuardTest {

    private static final String FULL =
            "今天下午广州下了很大的雨，我在老城区的骑楼下躲了半小时。"
                    + "八岁那年暑假我住在外婆家，老屋是青瓦的，下小雨的时候声音是脆的。"
                    + "后来老屋拆了，我就再没听过那种雨。";

    @Test
    @DisplayName("纯删除：把后半段删掉，应该通过")
    void pureDeletionPasses() {
        var result = SignGuard.check(FULL, "今天下午广州下了很大的雨，我在老城区的骑楼下躲了半小时。");
        assertThat(result.ok()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    @DisplayName("挑句子：按原文顺序挑两句拼起来，仍然是子序列，应该通过")
    void pickingSentencesPasses() {
        var result = SignGuard.check(FULL, "广州下了很大的雨。八岁那年暑假我住在外婆家。");
        assertThat(result.ok()).as(result.joined()).isTrue();
    }

    @Test
    @DisplayName("改标点不算造假：逗号改句号应该通过")
    void punctuationChangePasses() {
        var result = SignGuard.check(FULL, "今天下午广州下了很大的雨。我在老城区的骑楼下躲了半小时。");
        assertThat(result.ok()).as(result.joined()).isTrue();
    }

    @Test
    @DisplayName("加了一个词就作废：这是最重要的一条")
    void addedWordsFail() {
        var result = SignGuard.check(FULL,
                "今天下午广州下了很大的雨，这是八岁那年的味道。");
        assertThat(result.ok()).isFalse();
        assertThat(result.joined()).contains("全文没有的内容");
    }

    @Test
    @DisplayName("编一个原文没有的年龄，必须被抓住")
    void inventedNumberFails() {
        var result = SignGuard.check(FULL, "十岁那年暑假我住在外婆家。");
        assertThat(result.ok()).isFalse();
    }

    @Test
    @DisplayName("编一句原文没有的引语，必须被抓住")
    void inventedQuoteFails() {
        var result = SignGuard.check(FULL, "外婆说过「瓦上的雨是脆的」。");
        assertThat(result.ok()).isFalse();
        assertThat(result.joined()).contains("引语");
    }

    @Test
    @DisplayName("空展签不算通过")
    void blankSignFails() {
        assertThat(SignGuard.check(FULL, "   ").ok()).isFalse();
    }

    @Test
    @DisplayName("展签比全文还长，一定是在编")
    void longerThanSourceFails() {
        var result = SignGuard.check("下雨了。", "今天下午广州下了很大的雨，我在骑楼下躲了半小时。");
        assertThat(result.ok()).isFalse();
        assertThat(result.joined()).contains("只能做减法");
    }

    @Test
    @DisplayName("异常要能定位到具体是哪几个字，不能只说「不合规」")
    void violationLocatesTheOffendingText() {
        var result = SignGuard.check(FULL, "今天下午广州下了很大的雨，这是八岁那年的味道。");
        assertThat(result.joined()).contains("…");
    }

    @Test
    @DisplayName("实体抽取：中文数量词、数字、引语都要抽出来")
    void extractsEntities() {
        var entities = SignGuard.entities("八岁那年是 1998 年，他说「我走了」。");
        assertThat(entities).contains("八岁", "1998", "「我走了」");
    }
}
