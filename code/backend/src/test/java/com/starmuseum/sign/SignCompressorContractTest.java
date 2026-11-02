package com.starmuseum.sign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 压缩器契约：**任何一条真实全文，压出来的展签都必须过 SignGuard。**
 * 这不是抽查，是对种子库里每一条跑一遍。
 */
class SignCompressorContractTest {

    private static final int MAX_CHARS = 60;
    private final MockSignCompressor compressor = new MockSignCompressor();

    static Stream<org.junit.jupiter.params.provider.Arguments> seedFullTexts() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<org.junit.jupiter.params.provider.Arguments> out = new ArrayList<>();
        try (InputStream in = new ClassPathResource("seed/seed.json").getInputStream()) {
            JsonNode root = mapper.readTree(in);
            for (JsonNode node : root.path("exhibits")) {
                out.add(org.junit.jupiter.params.provider.Arguments.of(
                        node.path("id").asText(),
                        node.path("title").asText(),
                        node.path("full").asText()));
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0} 压出来的展签必须能过守卫")
    @MethodSource("seedFullTexts")
    void everySeedExhibitProducesAGuardedSign(String id, String title, String full) {
        String sign = compressor.compress(full, title, MAX_CHARS);

        assertThat(sign).as("%s 的展签不该为空", id).isNotBlank();
        assertThat(sign.length()).as("%s 的展签不该超过 %d 字", id, MAX_CHARS)
                .isLessThanOrEqualTo(MAX_CHARS);

        SignGuard.Result result = SignGuard.check(full, sign);
        assertThat(result.ok())
                .as("%s 的展签没过守卫：%s%n展签：%s", id, result.joined(), sign)
                .isTrue();
    }

    @ParameterizedTest(name = "{0} 的展签不得引入全文没有的实体")
    @MethodSource("seedFullTexts")
    void signIntroducesNoNewEntities(String id, String title, String full) {
        String sign = compressor.compress(full, title, MAX_CHARS);

        var added = SignGuard.entities(sign);
        added.removeAll(SignGuard.entities(full));

        assertThat(added).as("%s 的展签凭空多了：%s", id, added).isEmpty();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("压缩器是确定性的 —— 同一条输入每次结果相同")
    void compressorIsDeterministic() {
        String full = "凌晨四点下夜班，在楼下的便利店买一瓶水。收银台后面的阿姨在打瞌睡。"
                + "我把水放在台面上的时候，她醒了一下，说了句「这么晚啊」。";
        assertThat(compressor.compress(full, "测试", MAX_CHARS))
                .isEqualTo(compressor.compress(full, "测试", MAX_CHARS));
    }
}
