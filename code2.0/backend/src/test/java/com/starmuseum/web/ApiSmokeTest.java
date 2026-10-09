package com.starmuseum.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端冒烟：把前端要用的每一条链路真的跑一遍。
 * 其中最要紧的两条硬约束都用真实 WAV 文件验证：
 *   1）音频时长由服务端读文件决定，客户端说了不算；
 *   2）打捞的彩蛋保底。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiSmokeTest {

    private static final Path AUDIO = Path.of("data", "audio");
    /** 上传落盘的位置：测试自己一个目录（见 application-test.yml），不碰演示数据 */
    private static final Path UPLOADS = Path.of("target", "test-audio");
    private static final String DEVICE_KEY = "smoke-test-device-0001";

    @Autowired
    TestRestTemplate rest;

    static long sessionId;

    private JsonNode get(String path, boolean withSession) {
        HttpHeaders headers = new HttpHeaders();
        if (withSession) {
            headers.set("X-Session-Id", String.valueOf(sessionId));
        }
        ResponseEntity<JsonNode> res = rest.exchange(
                path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).as("%s 返回 %s", path, res.getStatusCode()).isTrue();
        return res.getBody().path("data");
    }

    /** 管理口要带口令；口令在 application-test.yml 里 */
    private ResponseEntity<JsonNode> getAdmin(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.set("X-Admin-Token", token);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }

    @Test
    @Order(1)
    @DisplayName("健康检查顺带报出馆藏规模")
    void ping() {
        JsonNode data = get("/api/ping", false);
        assertThat(data.path("exhibitCount").asInt()).isEqualTo(5);
        assertThat(data.path("heritageCount").asInt()).isEqualTo(3);
        assertThat(data.path("signProvider").asText()).isEqualTo("mock");
    }

    @Test
    @Order(2)
    @DisplayName("建会话：只要一个 deviceKey，不要邮箱密码")
    void createSession() {
        ResponseEntity<JsonNode> res = rest.postForEntity("/api/session",
                Map.of("deviceKey", DEVICE_KEY), JsonNode.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode data = res.getBody().path("data");
        sessionId = data.path("id").asLong();
        assertThat(sessionId).isPositive();
        assertThat(data.path("nickname").asText()).isNotBlank();
    }

    @Test
    @Order(3)
    @DisplayName("馆藏列表：卡片只给展签，不给全文")
    void listExhibits() {
        JsonNode list = get("/api/exhibits", true);
        assertThat(list.size()).isEqualTo(5);

        JsonNode first = list.get(0);
        assertThat(first.path("no").asText()).isNotBlank();
        assertThat(first.path("sign").asText()).isNotBlank();
        assertThat(first.path("trace").size()).isEqualTo(720);
        assertThat(first.path("audioUrl").asText()).startsWith("/audio/");
        // 卡片不该带全文 —— 展签和全文是两个接口，前端就没法做并排对比
        assertThat(first.has("full")).isFalse();
    }

    @Test
    @Order(4)
    @DisplayName("详情：这里才给全文")
    void exhibitDetail() {
        JsonNode detail = get("/api/exhibits/0249", true);
        assertThat(detail.path("title").asText()).isEqualTo("外婆家屋檐下的雨");
        assertThat(detail.path("full").asText()).isNotBlank();
        assertThat(detail.path("full").asText().length())
                .isGreaterThan(detail.path("sign").asText().length());
    }

    @Test
    @Order(5)
    @DisplayName("历史之声与用户投稿分开陈列")
    void heritageIsSeparate() {
        JsonNode list = get("/api/heritage", true);
        assertThat(list.size()).isEqualTo(3);
        // 展品必须有出处：来源和授权状态两个字段都要在，而且不能假装是真素材
        assertThat(list.get(0).path("source").asText()).contains("合成信号");
        assertThat(list.get(0).path("license").asText()).isEqualTo("非真实素材");
    }

    @Test
    @Order(6)
    @DisplayName("彩蛋保底：新用户第一次打捞必出历史之声")
    void firstDrawIsGuaranteedHeritage() {
        JsonNode draw = get("/api/draw", true);
        assertThat(draw.path("type").asText()).isEqualTo("heritage");
        assertThat(draw.path("heritage").path("title").asText()).isNotBlank();
    }

    @Test
    @Order(7)
    @DisplayName("打捞过的不会立刻重复")
    void drawDoesNotRepeatImmediately() {
        String first = get("/api/draw", true).path("heritage").path("slug").asText()
                + get("/api/draw", true).path("exhibit").path("id").asText();
        String second = get("/api/draw", true).path("heritage").path("slug").asText()
                + get("/api/draw", true).path("exhibit").path("id").asText();
        assertThat(second).as("连续两次不该捞到同一个东西").isNotEqualTo(first);
    }

    @Test
    @Order(8)
    @DisplayName("文字回音：写完立刻能在列表里看到")
    void postTextEcho() {
        long exhibitId = get("/api/exhibits/0249", true).path("id").asLong();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-Session-Id", String.valueOf(sessionId));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("body", "这条是我在测试里写的回音。");

        ResponseEntity<JsonNode> res = rest.exchange(
                "/api/exhibits/" + exhibitId + "/echoes", HttpMethod.POST,
                new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).as(res.getBody().toString()).isTrue();
        assertThat(res.getBody().path("data").path("kind").asText()).isEqualTo("text");

        JsonNode list = get("/api/exhibits/" + exhibitId + "/echoes", true);
        assertThat(list.size()).isGreaterThanOrEqualTo(4);
    }

    @Test
    @Order(9)
    @DisplayName("语音回音：时长由服务端从 WAV 里读出来（12 秒，合格）")
    void postVoiceEcho() {
        Assumptions.assumeTrue(Files.exists(AUDIO.resolve("echoes/echo-a.wav")),
                "先跑 node tools/gen-audio.mjs");

        long exhibitId = get("/api/exhibits/0251", true).path("id").asLong();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-Session-Id", String.valueOf(sessionId));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(AUDIO.resolve("echoes/echo-a.wav")));

        ResponseEntity<JsonNode> res = rest.exchange(
                "/api/exhibits/" + exhibitId + "/echoes", HttpMethod.POST,
                new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).as(String.valueOf(res.getBody())).isTrue();

        JsonNode echo = res.getBody().path("data");
        assertThat(echo.path("kind").asText()).isEqualTo("voice");
        assertThat(echo.path("seconds").asInt()).isEqualTo(12);
        assertThat(echo.path("trace").size()).isEqualTo(200);
        assertThat(echo.path("audioUrl").asText()).startsWith("/audio/echoes/");
    }

    @Test
    @Order(10)
    @DisplayName("时长硬约束由服务端裁决：9 秒的回音当展品上传会被拒")
    void serverEnforcesExhibitDuration() {
        Assumptions.assumeTrue(Files.exists(AUDIO.resolve("echoes/echo-b.wav")),
                "先跑 node tools/gen-audio.mjs");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-Session-Id", String.valueOf(sessionId));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(AUDIO.resolve("echoes/echo-b.wav")));
        body.add("title", "测试");
        body.add("full", "这是一段够长的测试文字，用来绕过最短字数检查，专心测时长这一条约束。");
        body.add("place", "测试地点");
        body.add("recordedOn", "2026-09-01");

        ResponseEntity<JsonNode> res = rest.exchange(
                "/api/exhibits", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
        assertThat(res.getBody().path("msg").asText()).contains("9");
        assertThat(res.getBody().path("msg").asText()).contains("10–60");
    }

    @Test
    @Order(11)
    @DisplayName("我的声音：列出我回应过的展品")
    void mine() {
        JsonNode mine = get("/api/mine", true);
        assertThat(mine.path("echoCount").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(mine.path("kept").size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @Order(12)
    @DisplayName("复审队列：没口令进不去，带对口令才拿得到")
    void reviewQueueIsReachable() {
        assertThat(getAdmin("/api/admin/queue", null).getStatusCode().value()).isEqualTo(401);
        assertThat(getAdmin("/api/admin/queue", "wrong-token").getStatusCode().value()).isEqualTo(401);

        ResponseEntity<JsonNode> ok = getAdmin("/api/admin/queue", "test-admin-token");
        assertThat(ok.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(ok.getBody().path("data").isArray()).isTrue();
    }

    @Test
    @Order(13)
    @DisplayName("删除：只有埋它的人能删，删完音频也一起走")
    void deleteOwnUpload() {
        Assumptions.assumeTrue(Files.exists(AUDIO.resolve("exhibits/well.wav")),
                "先跑 node tools/gen-audio.mjs");

        // 1）用本人的会话埋一段够长的声音
        HttpHeaders owner = new HttpHeaders();
        owner.setContentType(MediaType.MULTIPART_FORM_DATA);
        owner.set("X-Session-Id", String.valueOf(sessionId));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(AUDIO.resolve("exhibits/well.wav")));
        body.add("title", "准备删掉的一段");
        body.add("full", "这一段上传上来就是为了被删掉，所以故事也要写得够长，长到能过最短字数检查。");
        body.add("place", "测试地点");
        body.add("recordedOn", "2026-09-01");

        ResponseEntity<JsonNode> created = rest.exchange(
                "/api/exhibits", HttpMethod.POST, new HttpEntity<>(body, owner), JsonNode.class);
        assertThat(created.getStatusCode().is2xxSuccessful()).as(String.valueOf(created.getBody())).isTrue();

        JsonNode exhibit = created.getBody().path("data");
        String no = exhibit.path("no").asText();
        Path saved = UPLOADS.resolve(exhibit.path("audioUrl").asText().replace("/audio/", ""));
        assertThat(Files.exists(saved)).as("上传之后音频应该在磁盘上").isTrue();

        // 删除请求只带会话头 —— 不能把上传那套 multipart 的 Content-Type 一起带上，
        // 否则容器会去解析一个没有 boundary 的 multipart 请求
        HttpHeaders ownerDelete = new HttpHeaders();
        ownerDelete.set("X-Session-Id", String.valueOf(sessionId));

        // 2）换一个会话来删 —— 删不动。所有权认的是会话，不是请求里写了什么
        ResponseEntity<JsonNode> stranger = rest.postForEntity("/api/session",
                Map.of("deviceKey", "smoke-test-stranger-0002"), JsonNode.class);
        long strangerId = stranger.getBody().path("data").path("id").asLong();
        HttpHeaders other = new HttpHeaders();
        other.set("X-Session-Id", String.valueOf(strangerId));

        ResponseEntity<JsonNode> refused = rest.exchange(
                "/api/exhibits/" + no, HttpMethod.DELETE, new HttpEntity<>(other), JsonNode.class);
        assertThat(refused.getStatusCode().is4xxClientError()).isTrue();
        assertThat(refused.getBody().path("msg").asText()).contains("不是你埋下的");
        assertThat(Files.exists(saved)).as("被拒之后音频不该被删").isTrue();

        // 3）本人来删 —— 删得掉，磁盘上的音频也走
        ResponseEntity<JsonNode> deleted = rest.exchange(
                "/api/exhibits/" + no, HttpMethod.DELETE, new HttpEntity<>(ownerDelete), JsonNode.class);
        assertThat(deleted.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(deleted.getBody().path("data").path("removedEchoes").asInt()).isZero();
        assertThat(Files.exists(saved)).as("删掉之后音频不该留在磁盘上").isFalse();

        // 4）馆藏里没有了，我的声音里也没有了
        ResponseEntity<JsonNode> gone = rest.exchange(
                "/api/exhibits/" + no, HttpMethod.GET, new HttpEntity<>(ownerDelete), JsonNode.class);
        // 找不到是 4xx + 一句人话。这个项目里 BizException 一律走 400（不是 404），
        // 所以这里断言的是「删掉之后确实取不到了」，而不是某个具体状态码
        assertThat(gone.getStatusCode().is4xxClientError()).isTrue();
        assertThat(gone.getBody().path("msg").asText()).contains("馆藏里没有");

        for (JsonNode upload : get("/api/mine", true).path("uploads")) {
            assertThat(upload.path("no").asText()).isNotEqualTo(no);
        }
    }

    @Test
    @Order(14)
    @DisplayName("新的下限：12 秒能过（以前不行），14 个字仍然被拒")
    void newMinimums() {
        Assumptions.assumeTrue(Files.exists(AUDIO.resolve("echoes/echo-a.wav")),
                "先跑 node tools/gen-audio.mjs");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-Session-Id", String.valueOf(sessionId));

        // 12 秒：卡在旧下限（15）和新下限（10）之间，正好证明下限真的改了
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(AUDIO.resolve("echoes/echo-a.wav")));
        body.add("title", "十二秒的测试");
        body.add("full", "这一段十二秒，用来证明下限已经从十五秒改成了十秒，所以它现在能进馆。");
        body.add("place", "测试地点");
        body.add("recordedOn", "2026-09-01");

        ResponseEntity<JsonNode> ok = rest.exchange(
                "/api/exhibits", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(ok.getStatusCode().is2xxSuccessful()).as(String.valueOf(ok.getBody())).isTrue();
        assertThat(ok.getBody().path("data").path("status").asText()).isEqualTo("PASS");

        // 测完自己删掉，别在磁盘上留东西
        HttpHeaders clean = new HttpHeaders();
        clean.set("X-Session-Id", String.valueOf(sessionId));
        rest.exchange("/api/exhibits/" + ok.getBody().path("data").path("no").asText(),
                HttpMethod.DELETE, new HttpEntity<>(clean), JsonNode.class);

        // 故事 14 个字 —— 差一个字，仍然要被拦下
        MultiValueMap<String, Object> shortStory = new LinkedMultiValueMap<>();
        shortStory.add("file", new FileSystemResource(AUDIO.resolve("exhibits/well.wav")));
        shortStory.add("title", "故事短一个字");
        // 数准一点：13 个「十」加一个句号 = 14 个字，正好差一个字
        shortStory.add("full", "十".repeat(13) + "。");
        shortStory.add("place", "测试地点");
        shortStory.add("recordedOn", "2026-09-01");

        ResponseEntity<JsonNode> rejected = rest.exchange(
                "/api/exhibits", HttpMethod.POST, new HttpEntity<>(shortStory, headers), JsonNode.class);
        assertThat(rejected.getStatusCode().is4xxClientError()).isTrue();
        assertThat(rejected.getBody().path("msg").asText()).contains("15");
    }
}
