package com.starmuseum.service;

import com.starmuseum.common.TraceCodec;
import com.starmuseum.domain.Exhibit;
import com.starmuseum.domain.TargetType;
import com.starmuseum.repo.DrawLogRepository;
import com.starmuseum.repo.ExhibitRepository;
import com.starmuseum.repo.HeritageSoundRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 打捞去重必须**分类型**。
 *
 * exhibit 和 heritage 的自增 id 都从 1 开始，库里天然会有「展品 1 号」和「历史之声 1 号」。
 * 如果 recentIds 只保留数字，捞过历史之声 1 号就会把用户展品 1 号也排除掉 ——
 * 这是真实存在的、看不出来的 bug：用户只是觉得某些东西永远捞不到。
 */
class DrawServiceTest {

    private final DrawService service = new DrawService(
            mock(ExhibitRepository.class), mock(HeritageSoundRepository.class),
            mock(DrawLogRepository.class), mock(ExhibitService.class),
            mock(HeritageService.class), 0.12, 3);

    private static Exhibit exhibit(long id) {
        Exhibit e = new Exhibit(String.format("%04d", id), "展品 " + id, "展签",
                "全文全文全文全文全文全文全文全文全文全文全文", "地点", LocalDate.now(),
                "exhibits/x" + id + ".wav", 30, TraceCodec.encode(List.of(0.0)), null, "作者", true);
        setId(e, id);
        return e;
    }

    /** 测试里没有持久化，id 只能是 private 的 —— 用反射塞进去，不去改业务实体 */
    private static void setId(Exhibit exhibit, long id) {
        try {
            var field = Exhibit.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(exhibit, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("捞过历史之声 1 号，不能把用户展品 1 号也排掉")
    void heritageHistoryDoesNotExcludeExhibitWithSameId() {
        List<Exhibit> fresh = service.freshExhibits(
                List.of(exhibit(1L), exhibit(2L)), Set.of(DrawService.key(TargetType.HERITAGE, 1L)));

        assertThat(fresh).as("两类各自计数，展品 1 号仍然是新的").hasSize(2);
    }

    @Test
    @DisplayName("捞过展品 1 号，展品 1 号本身要被排除")
    void sameTypeSameIdIsExcluded() {
        List<Exhibit> fresh = service.freshExhibits(
                List.of(exhibit(1L), exhibit(2L)), Set.of(DrawService.key(TargetType.EXHIBIT, 1L)));

        assertThat(fresh).hasSize(1);
        assertThat(fresh.get(0).getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("池子都捞过了就放开，别让用户捞不到东西")
    void exhaustedPoolFallsBackToEverything() {
        List<Exhibit> pool = List.of(exhibit(1L), exhibit(2L));
        List<Exhibit> fresh = service.freshExhibits(pool, Set.of(
                DrawService.key(TargetType.EXHIBIT, 1L), DrawService.key(TargetType.EXHIBIT, 2L)));

        assertThat(fresh).hasSize(2);
    }

    @Test void firstDrawPrefersOrdinaryEvenWithFullHeritageProbability() {
        assertDrawType(0, 0, 1.0, Set.of(), "exhibit");
    }
    @Test void exhaustedHeritageDoesNotRepeatWhileOrdinaryIsFresh() {
        assertDrawType(8, 1, 1.0, Set.of(DrawService.key(TargetType.HERITAGE, 1L)), "exhibit");
    }
    @Test void thirdDrawGuaranteesHeritage() {
        assertDrawType(2, 0, 0.0, Set.of(), "heritage");
    }
    private void assertDrawType(long total, long historic, double rate, Set<String> recent, String expected) {
        var exhibits = mock(ExhibitRepository.class);
        var heritage = mock(HeritageSoundRepository.class);
        var logs = mock(DrawLogRepository.class);
        var session = mock(com.starmuseum.domain.Session.class);
        var h = mock(com.starmuseum.domain.HeritageSound.class);
        org.mockito.Mockito.when(session.getId()).thenReturn(99L);
        org.mockito.Mockito.when(h.getId()).thenReturn(1L);
        org.mockito.Mockito.when(exhibits.findByStatusOrderByCreatedAtDesc(Exhibit.AuditStatus.PASS)).thenReturn(List.of(exhibit(1)));
        org.mockito.Mockito.when(heritage.findAllByOrderBySortOrderAsc()).thenReturn(List.of(h));
        org.mockito.Mockito.when(logs.countBySessionId(99L)).thenReturn(total);
        org.mockito.Mockito.when(logs.countBySessionIdAndTargetType(99L, TargetType.HERITAGE)).thenReturn(historic);
        org.mockito.Mockito.when(logs.findBySessionIdOrderByCreatedAtDesc(99L)).thenReturn(
            recent.isEmpty() ? List.of() : List.of(new com.starmuseum.domain.DrawLog(99L, TargetType.HERITAGE, 1L)));
        var service = new DrawService(exhibits, heritage, logs, mock(ExhibitService.class), mock(HeritageService.class), rate, 3);
        var result = service.draw(session);
        assertThat(result.type()).isEqualTo(expected);
    }
}
