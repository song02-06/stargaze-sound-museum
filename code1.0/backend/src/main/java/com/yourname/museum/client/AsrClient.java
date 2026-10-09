package com.yourname.museum.client;

import java.nio.file.Path;

/**
 * 语音识别抽象。所有外部服务调用都必须收敛在 client 包里，
 * 这样答辩前任何一家服务出问题，都能在几分钟内换掉或关掉。
 */
public interface AsrClient {

    AsrResult transcribe(Path audioFile);

    String providerName();
}
