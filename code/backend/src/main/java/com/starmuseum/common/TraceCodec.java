package com.starmuseum.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 波形存取：700 多个 3 位小数，用逗号分隔比 JSON 更省，也不需要额外解析器。
 * 这些点是从真实 PCM 采样里抽出来的，不是画出来的。
 */
public final class TraceCodec {

    private TraceCodec() {
    }

    public static String encode(List<Double> trace) {
        if (trace == null || trace.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(trace.size() * 6);
        for (int i = 0; i < trace.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(trace.get(i));
        }
        return sb.toString();
    }

    public static List<Double> decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] parts = raw.split(",");
        List<Double> out = new ArrayList<>(parts.length);
        for (String p : parts) {
            try {
                out.add(Double.parseDouble(p.trim()));
            } catch (NumberFormatException ignored) {
                // 坏点跳过即可，波形少一个点看不出来
            }
        }
        return out;
    }
}
