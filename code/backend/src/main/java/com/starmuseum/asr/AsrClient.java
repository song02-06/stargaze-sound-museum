package com.starmuseum.asr;

import java.nio.file.Path;

/** 把一段音频转成文字。实现必须只接受 16k 单声道 16 位 PCM WAV。 */
public interface AsrClient {

    AsrResult transcribe(Path wavFile);

    String provider();

    /**
     * 这个实现产出的文本，能不能当作**这段音频的真实内容**来用。
     *
     * 真实服务（百度短语音）返回 true；
     * 离线兜底（Mock）返回 false —— 它返回的是固定的样例句子，
     * 拿它冒充用户说的话、再据以放行，等于没有审核。
     * 所以 false 的时候，调用方必须走人工复审。
     */
    default boolean reliable() {
        return true;
    }
}
