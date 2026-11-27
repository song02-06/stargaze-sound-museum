package com.starmuseum.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 一次完整的业务流：A 作者、B 听众、C 管理员。
 *
 * 这些用例对应报告里「修改后必须新增的有意义验证」：
 * 编辑本人可改他人不能改、编辑不新增记录、未通过不进公共查询；
 * 通知只在真正送达时产生、他人不能替我标已读；
 * 待审语音能在管理端拿到音频地址；编号不复用；删除清掉全部语音文件；检索可用。
 *
 * 刻意用一个**独立的数据库**（不同 context 缓存键），
 * 免得和 ApiSmokeTest 的「馆藏正好 5 件」互相干扰。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:museum-flow;DB_CLOSE_DELAY=-1",
        "museum.audio-root=./target/test-audio-flow"
})
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MuseumFlowTest {

    private static final Path AUDIO = Path.of("data", "audio");
    private static final Path UPLOADS = Path.of("target", "test-audio-flow");
    private static final String ADMIN = "test-admin-token";

    @Autowired
    TestRestTemplate rest;

    final Map<String, String> accountCookies = new java.util.HashMap<>();
    static long authorId;
    static long listenerId;
    /** A 的第一件投稿，后面编辑、回音都对着它 */
    static String mainNo;
    static long mainExhibitId;

    /* ---------------- 请求小工具 ---------------- */

    private ResponseEntity<JsonNode> call(HttpMethod method, String path, long sessionId,
                                          Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (sessionId > 0) {
            headers.set("X-Session-Id", String.valueOf(sessionId));
        }
        if (body instanceof Map || body instanceof List) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private JsonNode data(ResponseEntity<JsonNode> res) {
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("期望成功，实际 %s / %s", res.getStatusCode(), res.getBody()).isTrue();
        return res.getBody().path("data");
    }

    private long newSession(String deviceKey) {
        ResponseEntity<JsonNode> res = rest.postForEntity("/api/session",
                Map.of("deviceKey", deviceKey), JsonNode.class);
        long id = data(res).path("id").asLong();
        var registered = rest.postForEntity("/api/auth/register", Map.of("deviceKey", deviceKey,
                "username", "flow_" + id, "password", "test-pass-2026"), JsonNode.class);
        data(registered);
        accountCookies.put(String.valueOf(id), registered.getHeaders().getFirst("Set-Cookie").split(";")[0]);
        return id;
    }

    private ResponseEntity<JsonNode> upload(long sessionId, Path audio, String title, String full,
                                            String place, String soundType, Boolean confirmed) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-Session-Id", String.valueOf(sessionId));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(audio));
        body.add("title", title);
        body.add("full", full);
        body.add("place", place);
        body.add("recordedOn", LocalDate.now().minusDays(3).toString());
        if (soundType != null) {
            body.add("soundType", soundType);
        }
        if (confirmed != null) {
            body.add("originalConfirmed", confirmed.toString());
        }
        return rest.exchange("/api/exhibits", HttpMethod.POST, new HttpEntity<>(body, headers),
                JsonNode.class);
    }

    private ResponseEntity<JsonNode> sendTextEcho(long sessionId, String no, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-Session-Id", String.valueOf(sessionId));
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("body", body);
        return rest.exchange("/api/exhibits/" + no + "/echoes", HttpMethod.POST,
                new HttpEntity<>(form, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> sendVoiceEcho(long sessionId, String no) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-Session-Id", String.valueOf(sessionId));
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new FileSystemResource(AUDIO.resolve("echoes/echo-a.wav")));
        return rest.exchange("/api/exhibits/" + no + "/echoes", HttpMethod.POST,
                new HttpEntity<>(form, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> adminReview(String type, long id, boolean pass, String reason) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Token", ADMIN);
        String qs = "?pass=" + pass + "&reason=" + reason;
        return rest.exchange("/api/admin/review/" + type + "/" + id + qs, HttpMethod.POST,
                new HttpEntity<>(headers), JsonNode.class);
    }

    private JsonNode notificationsOf(long sessionId) {
        return data(call(HttpMethod.GET, "/api/mine/notifications", sessionId, null));
    }

    private JsonNode queue() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Token", ADMIN);
        ResponseEntity<JsonNode> res = rest.exchange("/api/admin/queue", HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class);
        return data(res);
    }

    private static Path disk(String audioUrl) {
        return UPLOADS.resolve(audioUrl.replace("/audio/", ""));
    }

    @BeforeAll
    void requireAudio() {
        Assumptions.assumeTrue(Files.exists(AUDIO.resolve("exhibits/well.wav")),
                "先跑 node tools/gen-audio.mjs");
        // TestRestTemplate 默认用 HttpURLConnection，发不出 PATCH（java.net 的老限制）。
        // 换成基于 java.net.http.HttpClient 的工厂，PATCH 才能真的打到服务端。
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        rest.getRestTemplate().getInterceptors().add((request, body, execution) -> {
            String cookie = accountCookies.get(request.getHeaders().getFirst("X-Session-Id"));
            if (cookie != null) request.getHeaders().set("Cookie", cookie);
            return execution.execute(request, body);
        });
    }

    /* ---------------- 用例 ---------------- */

    @Test
    @Order(1)
    @DisplayName("两个独立会话：作者 A、听众 B")
    void sessions() {
        authorId = newSession("flow-author-device");
        listenerId = newSession("flow-listener-device");
        assertThat(authorId).isPositive().isNotEqualTo(listenerId);
    }

    @Test
    @Order(2)
    @DisplayName("没有勾原创声明 → 服务端直接拒收，不落库")
    void uploadRequiresOriginalConfirmation() {
        ResponseEntity<JsonNode> res = upload(authorId, AUDIO.resolve("exhibits/well.wav"),
                "没勾声明", "这一段用来验证没有原创声明时服务端会直接拒收，不再往下走。",
                "测试地点", "ENVIRONMENT", null);

        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
        assertThat(res.getBody().path("msg").asText()).contains("确认");
    }

    @Test
    @Order(3)
    @DisplayName("演唱类投稿 → 强制转人工，并带上音频与语境进管理端队列")
    void singingGoesToManualReview() {
        JsonNode created = data(upload(authorId, AUDIO.resolve("exhibits/rain.wav"),
                "窗外的雨", "这一段是在老家屋檐下录的，雨点打在瓦片上的声音，很密。",
                "广州 · 老城区", "SINGING", true));

        mainNo = created.path("no").asText();
        mainExhibitId = created.path("id").asLong();
        assertThat(created.path("status").asText())
                .as("演唱类必须转人工，哪怕文字是干净的").isEqualTo("REVIEW");
        assertThat(created.path("soundType").asText()).isEqualTo("SINGING");

        JsonNode item = null;
        for (JsonNode node : queue()) {
            if ("exhibit".equals(node.path("type").asText())
                    && node.path("id").asLong() == mainExhibitId) {
                item = node;
            }
        }
        assertThat(item).as("待复审队列里应该有这件展品").isNotNull();
        assertThat(item.path("audioUrl").asText()).startsWith("/audio/");
        assertThat(item.path("seconds").asInt()).isPositive();
        assertThat(item.path("place").asText()).isNotBlank();
        assertThat(item.path("date").asText()).isNotBlank();
        assertThat(item.path("kind").asText()).isEqualTo("SINGING");
    }

    @Test
    @Order(4)
    @DisplayName("未通过审核的内容不进公共馆藏")
    void pendingContentIsNotPublic() {
        ResponseEntity<JsonNode> detail = call(HttpMethod.GET, "/api/exhibits/" + mainNo, 0, null);
        assertThat(detail.getStatusCode().is4xxClientError()).isTrue();

        for (JsonNode card : data(call(HttpMethod.GET, "/api/exhibits", 0, null))) {
            assertThat(card.path("no").asText()).isNotEqualTo(mainNo);
        }
    }

    @Test
    @Order(5)
    @DisplayName("管理员通过 → 入馆，作者收到审核结果消息")
    void adminApprovalNotifiesAuthor() {
        assertThat(adminReview("exhibit", mainExhibitId, true, "").getStatusCode().is2xxSuccessful())
                .isTrue();

        JsonNode detail = data(call(HttpMethod.GET, "/api/exhibits/" + mainNo, 0, null));
        assertThat(detail.path("status").asText()).isEqualTo("PASS");

        JsonNode box = notificationsOf(authorId);
        assertThat(box.path("unread").asInt()).isEqualTo(1);
        JsonNode first = box.path("items").get(0);
        assertThat(first.path("kind").asText()).isEqualTo("EXHIBIT_REVIEW_RESULT");
        assertThat(first.path("targetNo").asText()).isEqualTo(mainNo);
        assertThat(first.path("read").asBoolean()).isFalse();
    }

    @Test
    @Order(6)
    @DisplayName("只有接收人能把自己的消息标已读")
    void onlyRecipientCanMarkRead() {
        JsonNode first = notificationsOf(authorId).path("items").get(0);
        long notificationId = first.path("id").asLong();

        ResponseEntity<JsonNode> stranger = call(HttpMethod.PATCH,
                "/api/mine/notifications/" + notificationId + "/read", listenerId, null);
        assertThat(stranger.getStatusCode().is4xxClientError())
                .as("别人不能替我把消息标成已读").isTrue();

        JsonNode marked = data(call(HttpMethod.PATCH,
                "/api/mine/notifications/" + notificationId + "/read", authorId, null));
        assertThat(marked.path("read").asBoolean()).isTrue();
        assertThat(notificationsOf(authorId).path("unread").asInt()).isZero();
    }

    @Test
    @Order(7)
    @DisplayName("同一件投稿可以编辑：编号不变、记录数不增加、改完重新审核")
    void editKeepsIdentityAndReaudits() {
        long before = data(call(HttpMethod.GET, "/api/exhibits", 0, null)).size();
        JsonNode source = data(call(HttpMethod.GET, "/api/mine/uploads/" + mainNo, authorId, null));
        assertThat(source.path("title").asText()).isEqualTo("窗外的雨");

        JsonNode updated = data(call(HttpMethod.PATCH, "/api/exhibits/" + mainNo, authorId,
                Map.of("title", "窗外的雨（改过）",
                        "full", "改过一次：这一段是在老家屋檐下录的，雨点打在瓦片上的声音，很密。",
                        "place", "广州 · 老城区",
                        "recordedOn", LocalDate.now().minusDays(3).toString(),
                        "soundType", "ENVIRONMENT",
                        "originalConfirmed", true)));

        assertThat(updated.path("no").asText()).as("编号不能变").isEqualTo(mainNo);
        assertThat(updated.path("id").asLong()).isEqualTo(mainExhibitId);
        assertThat(updated.path("title").asText()).isEqualTo("窗外的雨（改过）");
        // 改成环境声、文字干净 → 这次不用人工，直接通过
        assertThat(updated.path("status").asText()).isEqualTo("PASS");
        assertThat(data(call(HttpMethod.GET, "/api/exhibits", 0, null)).size())
                .as("编辑不该新增一件展品").isEqualTo(before);
    }

    @Test
    @Order(8)
    @DisplayName("别人的投稿改不动")
    void strangerCannotEdit() {
        ResponseEntity<JsonNode> res = call(HttpMethod.PATCH, "/api/exhibits/" + mainNo, listenerId,
                Map.of("title", "我要改别人的", "full", "这一段文字足够长，长到能过最短字数检查，但是不该成功。",
                        "place", "测试地点", "recordedOn", LocalDate.now().minusDays(1).toString(),
                        "soundType", "ENVIRONMENT", "originalConfirmed", true));

        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
        assertThat(res.getBody().path("msg").asText()).contains("不是你埋下的");
    }

    @Test
    @Order(9)
    @DisplayName("别人的投稿也删不动、编辑源也读不到")
    void strangerCannotDeleteOrReadSource() {
        // 三条对外暴露的入口都要认所有权，只测 PATCH 会漏掉另外两条。
        ResponseEntity<JsonNode> read = call(HttpMethod.GET, "/api/mine/uploads/" + mainNo, listenerId, null);
        assertThat(read.getStatusCode().is4xxClientError()).as("读别人的编辑源应被拒绝").isTrue();
        assertThat(read.getBody().path("msg").asText()).contains("不是你埋下的");

        ResponseEntity<JsonNode> del = call(HttpMethod.DELETE, "/api/exhibits/" + mainNo, listenerId, null);
        assertThat(del.getStatusCode().is4xxClientError()).as("删别人的投稿应被拒绝").isTrue();
        assertThat(del.getBody().path("msg").asText()).contains("不是你埋下的");

        // 拒绝之后那件东西必须还在——否则「报错了但已经删了」比放行更糟。
        JsonNode still = data(call(HttpMethod.GET, "/api/exhibits/" + mainNo, 0, null));
        assertThat(still.path("no").asText()).isEqualTo(mainNo);
    }

    @Test
    @Order(10)
    @DisplayName("改出问题就退回审核：命中拦截词 → 驳回，并从公共馆藏消失")
    void offendingEditHidesFromPublic() {
        JsonNode updated = data(call(HttpMethod.PATCH, "/api/exhibits/" + mainNo, authorId,
                Map.of("title", "窗外的雨（改坏了）",
                        "full", "这一段里故意写了加微信三个字，用来证明编辑之后会重新审核，而不是沿用上一次的结论。",
                        "place", "广州 · 老城区",
                        "recordedOn", LocalDate.now().minusDays(3).toString(),
                        "soundType", "ENVIRONMENT",
                        "originalConfirmed", true)));

        assertThat(updated.path("status").asText()).isEqualTo("REJECT");
        assertThat(updated.path("auditNote").asText()).isNotBlank();

        ResponseEntity<JsonNode> detail = call(HttpMethod.GET, "/api/exhibits/" + mainNo, 0, null);
        assertThat(detail.getStatusCode().is4xxClientError()).isTrue();

        // 作者自己仍然看得到、还能再改回去
        JsonNode mine = data(call(HttpMethod.GET, "/api/mine", authorId, null));
        boolean found = false;
        for (JsonNode upload : mine.path("uploads")) {
            if (mainNo.equals(upload.path("no").asText())) {
                found = true;
                assertThat(upload.path("status").asText()).isEqualTo("REJECT");
            }
        }
        assertThat(found).isTrue();

        // 改回去 → 重新通过
        JsonNode back = data(call(HttpMethod.PATCH, "/api/exhibits/" + mainNo, authorId,
                Map.of("title", "窗外的雨",
                        "full", "改回来了：这一段是在老家屋檐下录的，雨点打在瓦片上的声音，很密。",
                        "place", "广州 · 老城区",
                        "recordedOn", LocalDate.now().minusDays(3).toString(),
                        "soundType", "ENVIRONMENT",
                        "originalConfirmed", true)));
        assertThat(back.path("status").asText()).isEqualTo("PASS");
    }

    @Test
    @Order(11)
    @DisplayName("标题和地点也要进规则审核，不能只查故事")
    void titleAndPlaceAreAudited() {
        JsonNode bad = data(upload(authorId, AUDIO.resolve("exhibits/store.wav"),
                "正常标题", "故事这一段写得干干净净，长度也够，问题只出在地点上。",
                "办证大厅", "ENVIRONMENT", true));
        assertThat(bad.path("status").asText()).as("地点命中拦截词也要被拦").isEqualTo("REJECT");

        JsonNode badTitle = data(upload(authorId, AUDIO.resolve("exhibits/bus.wav"),
                "加微信", "故事这一段写得干干净净，长度也够，问题只出在标题上。",
                "测试地点", "ENVIRONMENT", true));
        assertThat(badTitle.path("status").asText()).as("标题命中拦截词也要被拦").isEqualTo("REJECT");
    }

    @Test
    @Order(12)
    @DisplayName("文字回音通过才通知作者；语音回音（未配 ASR）转人工，不冒充送达")
    void echoNotificationsOnlyWhenDelivered() {
        JsonNode text = data(sendTextEcho(listenerId, mainNo, "这段雨声我也听过。"));
        assertThat(text.path("status").asText()).isEqualTo("PASS");
        assertThat(text.path("authorId").asLong()).isEqualTo(listenerId);

        int inboxAfterText = notificationsOf(authorId).path("items").size();
        assertThat(notificationsOf(authorId).path("items").get(0).path("kind").asText())
                .isEqualTo("ECHO_RECEIVED");

        JsonNode voice = data(sendVoiceEcho(listenerId, mainNo));
        assertThat(voice.path("status").asText())
                .as("没配真实 ASR 时语音回音必须转人工").isEqualTo("REVIEW");
        assertThat(voice.path("body").isNull() || voice.path("body").isMissingNode())
                .as("不能拿样例句子冒充转写").isTrue();

        assertThat(notificationsOf(authorId).path("items").size())
                .as("待审的回音不算送达").isEqualTo(inboxAfterText);
    }

    @Test
    @Order(13)
    @DisplayName("待审语音回音在管理端能拿到可播放的音频地址")
    void pendingVoiceEchoExposesAudio() {
        JsonNode item = null;
        for (JsonNode node : queue()) {
            if ("echo".equals(node.path("type").asText())
                    && "VOICE".equals(node.path("kind").asText())) {
                item = node;
            }
        }
        assertThat(item).as("待审队列里应该有语音回音").isNotNull();
        assertThat(item.path("audioUrl").asText()).startsWith("/audio/echoes/");
        assertThat(item.path("seconds").asInt()).isPositive();
    }

    @Test
    @Order(14)
    @DisplayName("人工通过语音回音 → 发送人收到结果，作者收到回音，公开列表可见")
    void manualEchoPassNotifiesBothSides() {
        JsonNode pending = null;
        for (JsonNode node : queue()) {
            if ("echo".equals(node.path("type").asText())) {
                pending = node;
            }
        }
        assertThat(pending).isNotNull();
        long echoId = pending.path("id").asLong();

        int beforeAuthor = notificationsOf(authorId).path("items").size();
        int beforeListener = notificationsOf(listenerId).path("items").size();

        assertThat(adminReview("echo", echoId, true, "").getStatusCode().is2xxSuccessful()).isTrue();

        JsonNode listenerBox = notificationsOf(listenerId);
        assertThat(listenerBox.path("items").size()).isEqualTo(beforeListener + 1);
        assertThat(listenerBox.path("items").get(0).path("kind").asText())
                .isEqualTo("ECHO_REVIEW_RESULT");

        JsonNode authorBox = notificationsOf(authorId);
        assertThat(authorBox.path("items").size()).isEqualTo(beforeAuthor + 1);
        assertThat(authorBox.path("items").get(0).path("kind").asText()).isEqualTo("ECHO_RECEIVED");

        // 收件箱里能看到这条回音，并且能定位回展品
        JsonNode inbox = data(call(HttpMethod.GET, "/api/mine/inbox", authorId, null));
        boolean located = false;
        for (JsonNode item : inbox) {
            if (mainNo.equals(item.path("exhibitNo").asText())) {
                located = true;
            }
        }
        assertThat(located).as("收件箱内容：%s", inbox).isTrue();

        assertThat(data(call(HttpMethod.GET, "/api/exhibits/" + mainNo + "/echoes", 0, null)).size())
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    @Order(15)
    @DisplayName("驳回必须写原因；已决定的记录不能再被改结论")
    void rejectNeedsReasonAndReviewIsFinal() {
        // 先造一条真正在待审状态的语音回音
        JsonNode created = data(upload(authorId, AUDIO.resolve("exhibits/bus.wav"),
                "驳回测试", "这一段用来验证驳回必须填原因，所以故事也要写得够长才行。",
                "测试地点", "ENVIRONMENT", true));
        JsonNode pendingEcho = data(sendVoiceEcho(listenerId, created.path("no").asText()));
        assertThat(pendingEcho.path("status").asText()).isEqualTo("REVIEW");
        long echoId = pendingEcho.path("id").asLong();

        ResponseEntity<JsonNode> noReason = adminReview("echo", echoId, false, "");
        assertThat(noReason.getStatusCode().is4xxClientError()).isTrue();
        assertThat(noReason.getBody().path("msg").asText()).contains("原因");

        // 已经 PASS 的那条展品再被「复审」→ 提示刷新，而不是覆盖之前的结论
        ResponseEntity<JsonNode> stale = adminReview("exhibit", mainExhibitId, false, "我想改结论");
        assertThat(stale.getStatusCode().is4xxClientError()).isTrue();
        assertThat(stale.getBody().path("msg").asText()).contains("刷新");

        // 补上原因就能驳回了
        assertThat(adminReview("echo", echoId, false, "听不清内容").getStatusCode().is2xxSuccessful())
                .isTrue();
        assertThat(data(call(HttpMethod.GET, "/api/exhibits/" + created.path("no").asText()
                + "/echoes", 0, null)).size()).isZero();

        data(call(HttpMethod.DELETE, "/api/exhibits/" + created.path("no").asText(), authorId, null));
    }

    @Test
    @Order(16)
    @DisplayName("删除展品：全部关联语音文件一起消失，审核流水保留")
    void deleteCleansAllEchoAudio() throws Exception {
        JsonNode created = data(upload(authorId, AUDIO.resolve("exhibits/typhoon.wav"),
                "准备删掉的一段", "这一段上传上来就是为了被删掉，所以故事也要写得够长，长到能过检查。",
                "测试地点", "ENVIRONMENT", true));
        String no = created.path("no").asText();
        Path exhibitAudio = disk(created.path("audioUrl").asText());
        assertThat(Files.exists(exhibitAudio)).isTrue();

        // 一条文字回音（没有音频）+ 一条待审语音回音（已落盘）。
        // 只清 PASS 的回音就会把后者的文件留在磁盘上。
        JsonNode textEcho = data(sendTextEcho(listenerId, no, "留一句就走。"));
        assertThat(textEcho.path("status").asText()).isEqualTo("PASS");
        JsonNode voice = data(sendVoiceEcho(listenerId, no));
        assertThat(voice.path("status").asText()).isEqualTo("REVIEW");
        Path voiceAudio = disk(voice.path("audioUrl").asText());
        assertThat(Files.exists(voiceAudio)).as("语音回音已经落盘").isTrue();

        JsonNode deleted = data(call(HttpMethod.DELETE, "/api/exhibits/" + no, authorId, null));
        assertThat(deleted.path("removedEchoes").asInt()).isEqualTo(1);

        assertThat(Files.exists(exhibitAudio)).as("展品音频要清掉").isFalse();
        assertThat(Files.exists(voiceAudio)).as("待审语音回音的文件也要清掉").isFalse();

        ResponseEntity<JsonNode> gone = call(HttpMethod.GET, "/api/exhibits/" + no, 0, null);
        assertThat(gone.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    @Order(17)
    @DisplayName("删除最大编号之后，新投稿不会重用旧编号")
    void numbersAreNotReused() {
        JsonNode first = data(upload(authorId, AUDIO.resolve("exhibits/well.wav"),
                "编号测试一", "这一段用来证明删掉最大编号之后新投稿不会重用那个号码。",
                "测试地点", "ENVIRONMENT", true));
        String no = first.path("no").asText();

        data(call(HttpMethod.DELETE, "/api/exhibits/" + no, authorId, null));

        JsonNode second = data(upload(authorId, AUDIO.resolve("exhibits/well.wav"),
                "编号测试二", "这一段用来证明删掉最大编号之后新投稿不会重用那个号码。",
                "测试地点", "ENVIRONMENT", true));
        assertThat(second.path("no").asText()).as("编号不复用").isNotEqualTo(no);

        data(call(HttpMethod.DELETE, "/api/exhibits/" + second.path("no").asText(), authorId, null));
    }

    @Test
    @Order(18)
    @DisplayName("馆藏检索：标题/故事/地点命中，检索不到就是空列表")
    void searchFindsByKeyword() {
        JsonNode hit = data(call(HttpMethod.GET, "/api/exhibits?q=屋檐", 0, null));
        assertThat(hit.size()).isGreaterThanOrEqualTo(1);

        JsonNode miss = data(call(HttpMethod.GET, "/api/exhibits?q=这个词肯定搜不到", 0, null));
        assertThat(miss.size()).isZero();

        // 检索只覆盖已通过的内容：待审/驳回的不该被搜出来
        for (JsonNode card : data(call(HttpMethod.GET, "/api/exhibits?q=加微信", 0, null))) {
            assertThat(card.path("title").asText()).doesNotContain("加微信");
        }
    }

    @Test
    @Order(19)
    @DisplayName("打捞去重分类型：EXHIBIT 与 HERITAGE 的同号 id 是两个对象")
    void drawDedupeSeparatesTypes() {
        // 这里验证的是可见行为：连续打捞不会因为「历史之声 1 号」而排掉「用户展品 1 号」，
        // 也就是两类各自独立计数。具体键的构造在 DrawServiceTest 里做确定性验证。
        JsonNode first = data(call(HttpMethod.GET, "/api/draw", authorId, null));
        JsonNode second = data(call(HttpMethod.GET, "/api/draw", authorId, null));

        assertThat(first.path("type").asText()).isNotBlank();
        assertThat(second.path("type").asText()).isNotBlank();
        assertThat(first.path("type").asText() + ":" + targetId(first))
                .isNotEqualTo(second.path("type").asText() + ":" + targetId(second));
    }

    private static long targetId(JsonNode draw) {
        return "heritage".equals(draw.path("type").asText())
                ? draw.path("heritage").path("id").asLong()
                : draw.path("exhibit").path("id").asLong();
    }
}
