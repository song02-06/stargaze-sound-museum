package com.yourname.museum.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

/**
 * 模拟转写：不需要任何 Key、不联网。
 * 它的作用有两个 —— 开发期跑通整条链路，以及答辩断网时的兜底。
 */
@Service
@ConditionalOnProperty(name = "museum.asr.provider", havingValue = "mock", matchIfMissing = true)
public class MockAsrClient implements AsrClient {

    private static final List<String> SAMPLES = List.of(
            "今天路过以前的小学，门口那棵树还在，就是比以前高了很多。",
            "我想对三年前的自己说，别怕，你后来真的做到了。",
            "外婆家的老式挂钟每到整点就会响，现在那个声音只能在记忆里找了。",
            "毕业那天我们都没哭，就是一直说以后要常联系。",
            "第一次一个人坐火车去很远的地方，半夜醒来看到窗外的灯，觉得特别自由。"
    );

    private final Random random = new Random();

    @Override
    public AsrResult transcribe(Path audioFile) {
        long start = System.currentTimeMillis();
        try {
            Thread.sleep(120);   // 模拟网络往返，方便前端看到 loading 状态
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String text = SAMPLES.get(random.nextInt(SAMPLES.size()));
        return AsrResult.ok(text, (int) (System.currentTimeMillis() - start));
    }

    @Override
    public String providerName() {
        return "mock";
    }
}
