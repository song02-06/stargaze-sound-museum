package com.starmuseum.media;

import com.starmuseum.common.BizException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 从 WAV 文件里读时长、声道、采样率，并抽出波形。
 *
 * 为什么要后端自己读：客户端报上来的时长是不可信的。
 * 「主声音 15–60 秒」是产品定义里的硬约束，硬约束必须由服务端裁决，
 * 否则改一下请求就绕过去了。波形同理 —— 界面上的声波应该来自真实采样。
 *
 * 只处理 PCM WAV（前端录音已统一转成 16k 单声道 WAV）。
 */
public final class WavSupport {

    public record Info(double seconds, int sampleRate, int channels, List<Double> trace) {
    }

    private WavSupport() {
    }

    public static Info read(Path file, int tracePoints) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new BizException("读不到音频文件");
        }
        if (bytes.length < 44) {
            throw new BizException("音频文件不完整");
        }

        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (!"RIFF".equals(ascii(bytes, 0)) || !"WAVE".equals(ascii(bytes, 8))) {
            throw new BizException("这不是 WAV 文件");
        }

        int sampleRate = 0;
        int channels = 0;
        int bitsPerSample = 0;
        int dataStart = -1;
        int dataLength = 0;

        int pos = 12;
        while (pos + 8 <= bytes.length) {
            String chunkId = ascii(bytes, pos);
            int chunkSize = buf.getInt(pos + 4);
            int body = pos + 8;

            if ("fmt ".equals(chunkId) && body + 16 <= bytes.length) {
                channels = buf.getShort(body + 2) & 0xFFFF;
                sampleRate = buf.getInt(body + 4);
                bitsPerSample = buf.getShort(body + 14) & 0xFFFF;
            } else if ("data".equals(chunkId)) {
                dataStart = body;
                dataLength = Math.min(chunkSize, bytes.length - body);
                break;
            }

            pos = body + chunkSize + (chunkSize % 2);
        }

        if (sampleRate <= 0 || channels <= 0 || dataStart < 0) {
            throw new BizException("WAV 头信息不完整");
        }
        if (bitsPerSample != 16) {
            throw new BizException("目前只支持 16 位 PCM 的 WAV");
        }

        int frameBytes = 2 * channels;
        int frames = dataLength / frameBytes;
        if (frames <= 0) {
            throw new BizException("这段音频是空的");
        }

        double seconds = frames / (double) sampleRate;
        List<Double> trace = extractTrace(buf, dataStart, frames, channels, tracePoints);
        return new Info(seconds, sampleRate, channels, trace);
    }

    /** 每段取绝对值最大的采样、保留符号 —— 示波器上那条线 */
    private static List<Double> extractTrace(ByteBuffer buf, int dataStart, int frames,
                                             int channels, int points) {
        List<Double> trace = new ArrayList<>(points);
        for (int p = 0; p < points; p++) {
            int from = (int) ((long) p * frames / points);
            int to = Math.max(from + 1, (int) ((long) (p + 1) * frames / points));
            double best = 0;
            for (int i = from; i < to && i < frames; i++) {
                // 多声道只取第一声道
                double v = buf.getShort(dataStart + i * 2 * channels) / 32768.0;
                if (Math.abs(v) > Math.abs(best)) {
                    best = v;
                }
            }
            trace.add(Math.round(best * 1000) / 1000.0);
        }
        return trace;
    }

    private static String ascii(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
    }
}
