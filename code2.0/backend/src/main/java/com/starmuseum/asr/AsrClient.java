package com.starmuseum.asr;

import java.nio.file.Path;

/** 把一段音频转成文字。实现必须只接受 16k 单声道 16 位 PCM WAV。 */
public interface AsrClient {

    AsrResult transcribe(Path wavFile);

    String provider();
}
