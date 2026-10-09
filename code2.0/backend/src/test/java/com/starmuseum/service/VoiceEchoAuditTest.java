package com.starmuseum.service;

import com.starmuseum.asr.AsrClient;
import com.starmuseum.asr.AsrResult;
import com.starmuseum.common.TraceCodec;
import com.starmuseum.domain.AuditRecord;
import com.starmuseum.domain.Echo;
import com.starmuseum.domain.Exhibit;
import com.starmuseum.domain.Session;
import com.starmuseum.domain.TargetType;
import com.starmuseum.media.FileStorageService;
import com.starmuseum.repo.EchoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 语音回音的审核规则。
 *
 * 这是整个 ASR 接线里最要紧的一条：
 * **转写失败 ≠ 内容没问题。** 语音回音只有转写这一条审核依据，
 * 转写不出来就意味着这段音频从没被人看过 —— 它必须转人工复审，
 * 绝不能因为「转写挂了」就默认放行。
 *
 * （展品不一样：展品的主要审核对象是用户手写的全文，
 *   转写只是附加的隐私检查，失败不拦住展品本身。这个差异是刻意的。）
 */
class VoiceEchoAuditTest {

    private static final Path SAMPLE = Path.of("data", "audio", "echoes", "echo-a.wav");

    private final EchoRepository echoes = mock(EchoRepository.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final AuditService audit = mock(AuditService.class);

    private EchoService serviceWith(AsrClient client) {
        when(echoes.save(any(Echo.class))).thenAnswer(inv -> inv.getArgument(0));
        when(storage.save(any(), eq("echoes"))).thenReturn("echoes/unit-test.wav");
        when(storage.resolve("echoes/unit-test.wav")).thenReturn(SAMPLE);
        return new EchoService(echoes, storage, audit, client, 10, 30);
    }

    private static Exhibit someExhibit() {
        return new Exhibit("9001", "测试展品", "展签", "全文全文全文全文全文全文全文全文全文全文",
                "测试地点", LocalDate.now(), "exhibits/x.wav", 30,
                TraceCodec.encode(List.of(0.0, 0.5, -0.5)), null, "测试", true);
    }

    private static Session someSession() {
        return new Session("unit-test-device", "测试用户");
    }

    @Test
    @DisplayName("转写失败 → 必须转人工复审，且不得留下任何正文")
    void asrFailureSendsVoiceEchoToManualReview() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "echo.wav", "audio/wav", Files.readAllBytes(SAMPLE));

        AsrClient brokenClient = new AsrClient() {
            @Override
            public AsrResult transcribe(Path wavFile) {
                return AsrResult.fail("模拟：识别服务超时", 1234);
            }

            @Override
            public String provider() {
                return "broken";
            }
        };

        Echo saved = serviceWith(brokenClient).addVoice(someSession(), someExhibit(), file);

        assertThat(saved.getStatus())
                .as("转写失败的语音回音不能直接放行")
                .isEqualTo(Echo.AuditStatus.REVIEW);
        assertThat(saved.getBody()).as("没转写出来就不该有正文").isNull();
        assertThat(saved.getAuditReason()).contains("人工");

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        // 注意用 any() 而不是 anyLong()：这里的回音还没落库，id 是 null
        verify(audit).recordWithCost(eq(TargetType.ECHO), any(), eq(AuditRecord.Stage.ASR),
                eq(AuditRecord.Verdict.REVIEW), anyString(), eq(1234L), eq("asr:broken"));
        // 转写都失败了，就不该再拿空文本去跑规则路和 AI 路
        verify(audit, never()).auditText(any(), any(), anyString());
    }

    @Test
    @DisplayName("转写成功 → 用转写文本走审核，并把文本存进正文")
    void asrSuccessAuditsTheTranscript() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "echo.wav", "audio/wav", Files.readAllBytes(SAMPLE));

        AsrClient goodClient = new AsrClient() {
            @Override
            public AsrResult transcribe(Path wavFile) {
                return AsrResult.ok("这是转写出来的文字。", List.of("这是转写出来的文字。"), 800);
            }

            @Override
            public String provider() {
                return "stub";
            }
        };

        when(audit.auditText(any(), any(), anyString()))
                .thenReturn(new AuditService.Outcome(AuditService.Verdict.PASS, null, List.of()));

        Echo saved = serviceWith(goodClient).addVoice(someSession(), someExhibit(), file);

        assertThat(saved.getBody()).isEqualTo("这是转写出来的文字。");
        assertThat(saved.getStatus()).isEqualTo(Echo.AuditStatus.PASS);
        // 关键：审核的对象必须是**转写文本**，不是空字符串
        verify(audit).auditText(eq(TargetType.ECHO), any(), eq("这是转写出来的文字。"));
    }
}
