package com.yourname.museum.common;

import java.util.List;

/**
 * 敏感词检索引擎 —— 审核「规则路」的核心抽象。
 *
 * <p>抽出接口是为了让引擎可替换，也让「降级能力」这件事有明确落点：
 * 默认用 {@link HoubbSensitiveWordEngine}（词库大、支持变体识别），
 * 需要零依赖或离线演示时切到 {@link DfaSensitiveWordEngine}。
 * 切换方式：{@code museum.audit.engine = houbb | dfa}。
 */
public interface SensitiveWordEngine {

    /** 返回命中的词（按出现顺序去重）；无命中返回空列表。 */
    List<String> match(String text);

    /** 当前生效的词条总数，用于健康检查展示。 */
    int wordCount();

    /** 引擎名，写进日志与 /api/ping，便于确认当前跑的是哪一套。 */
    String name();
}
