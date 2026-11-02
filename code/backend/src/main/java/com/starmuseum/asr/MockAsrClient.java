package com.starmuseum.asr;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 离线兜底：不联网、不需要 Key、不花钱。
 *
 * 它的两个用途：
 * 1）开发和测试期跑通整条链路，不用等额度、不怕欠费；
 * 2）答辩现场断网时的兜底 —— 演示不能因为网络挂掉。
 *
 * ⚠️ 它返回的是**固定的样例文本，不是真的在识别**。
 * 因此 {@link #reliable()} 返回 false：
 * 语音回音在它下面**一律转人工复审**，绝不能拿样例句子当用户说的话放过去。
 * 流水里记 operator = "asr:mock"，一眼能看出这条没过真模型。
 */
public class MockAsrClient implements AsrClient {

    private static final List<String> SAMPLES = List.of(
            "（演示用样例文本，不是真的识别结果）我外婆家也是这种瓦，下雨的时候声音是脆的。",
            "（演示用样例文本，不是真的识别结果）这段话让我想起小时候住的那条巷子。"
    );

    @Override
    public AsrResult transcribe(Path wavFile) {
        long start = System.currentTimeMillis();
        try {
            // 假装有一次网络往返，让前端的 loading 状态能被看见
            Thread.sleep(120);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String text = SAMPLES.get(ThreadLocalRandom.current().nextInt(SAMPLES.size()));
        return AsrResult.ok(text, List.of(text), (int) (System.currentTimeMillis() - start));
    }

    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public boolean reliable() {
        return false;
    }
}
