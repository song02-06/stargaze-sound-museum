package com.starmuseum.service;
import com.starmuseum.domain.*;
import com.starmuseum.repo.*;
import com.starmuseum.media.FileStorageService;
import com.starmuseum.asr.AsrClient;
import com.starmuseum.config.ExhibitNoAllocator;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;
class ExhibitReviewTest {
 @Test void editingTextCannotClearUnreviewedAudio() {
  var repo = mock(ExhibitRepository.class);
  var audit = mock(AuditService.class);
  var signs = mock(SignService.class);
  var owner = mock(Session.class); when(owner.getId()).thenReturn(99L);
  var exhibit = new Exhibit("9001", "标题", "展签", "这是一段用于测试审核状态的完整故事。", "地点", LocalDate.now(), "x.wav", 30, "[]", 99L, "作者", false);
  exhibit.requireAudioReview("音频里可能录到了隐私信息");
  when(repo.findByNo("9001")).thenReturn(Optional.of(exhibit));
  when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
  when(signs.compress(anyString(), anyString(), nullable(Long.class))).thenReturn(new SignService.Result("展签", "mock", true, List.of(), false));
  when(audit.auditExhibit(nullable(Long.class), anyString(), anyString(), anyString())).thenReturn(new AuditService.Outcome(AuditService.Verdict.PASS, "", List.of()));
  var service = new ExhibitService(repo, mock(EchoRepository.class), mock(FileStorageService.class), audit, signs, mock(AsrClient.class), mock(NotificationService.class), mock(ExhibitNoAllocator.class), true, 10, 60, 20, 60);
  service.update(owner, "9001", "新标题", "这是一段修改后仍然完整且足够长度的故事。", "地点", LocalDate.now(), Exhibit.SoundType.SPEECH, true);
  assertThat(exhibit.getStatus()).isEqualTo(Exhibit.AuditStatus.REVIEW);
  exhibit.clearAudioReview();
  exhibit.approve(); // 模拟人工审核通过同时更新状态与音频事项
  service.update(owner, "9001", "新标题", "这是一段修改后仍然完整且足够长度的故事。", "地点", LocalDate.now(), Exhibit.SoundType.SPEECH, true);
  assertThat(exhibit.getStatus()).isEqualTo(Exhibit.AuditStatus.PASS);
 }
}

