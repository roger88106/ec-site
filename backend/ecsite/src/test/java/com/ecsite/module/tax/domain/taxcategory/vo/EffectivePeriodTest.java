package com.ecsite.module.tax.domain.taxcategory.vo;

import com.ecsite.module.tax.domain.TaxErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 適用期間の単体テスト
 */
class EffectivePeriodTest {

    // ============================== 定数 ==============================

    /**
     * 期間の端に使う時刻（早いほう）
     *
     * <p>日本時間の 2027-04-01 00:00。
     */
    private static final Instant T1 = Instant.parse("2027-03-31T15:00:00Z");

    /**
     * 期間の端に使う時刻（遅いほう）
     *
     * <p>日本時間の 2028-04-01 00:00。
     */
    private static final Instant T2 = Instant.parse("2028-03-31T15:00:00Z");

    /**
     * 遠い未来の時刻
     */
    private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T00:00:00Z");

    // ============================== テスト ==============================

    @Nested
    @DisplayName("作成")
    class Create {

        @Test
        @DisplayName("AC-2 開始が終了より前の期間を作れる")
        void acceptsStartBeforeEnd() {
            // 操作：開始が終了より前の期間を作る
            var period = EffectivePeriod.of(T1, T2);

            // 確認：渡した開始と終了を持つ
            assertThat(period.effectiveFrom()).isEqualTo(T1);
            assertThat(period.effectiveTo()).isEqualTo(T2);
        }

        @Test
        @DisplayName("AC-2 開始と終了が同じ期間は拒否される")
        void rejectsEmptyPeriod() {
            // 操作と確認：業務のエラー INVALID_EFFECTIVE_PERIOD で拒否される
            assertBusinessError(() -> EffectivePeriod.of(T1, T1), TaxErrorCode.INVALID_EFFECTIVE_PERIOD);
        }

        @Test
        @DisplayName("AC-2 開始が終了より後の期間は拒否される")
        void rejectsStartAfterEnd() {
            // 操作と確認：業務のエラー INVALID_EFFECTIVE_PERIOD で拒否される
            assertBusinessError(() -> EffectivePeriod.of(T2, T1), TaxErrorCode.INVALID_EFFECTIVE_PERIOD);
        }

        @Test
        @DisplayName("AC-2 開始・終了とも空白にできる")
        void acceptsUnbounded() {
            // 操作：開始・終了とも空白の期間を作る
            var period = EffectivePeriod.of(null, null);

            // 確認：開始・終了とも空白のまま
            assertThat(period.effectiveFrom()).isNull();
            assertThat(period.effectiveTo()).isNull();
        }
    }

    @Nested
    @DisplayName("ある時点を含むか")
    class Contains {

        @Test
        @DisplayName("AC-2 開始ちょうどは含む")
        void includesStart() {
            // 操作と確認：開始の時点を含む
            assertThat(EffectivePeriod.of(T1, T2).contains(T1)).isTrue();
        }

        @Test
        @DisplayName("AC-2 開始より前は含まない")
        void excludesBeforeStart() {
            // 操作と確認：開始の直前を含まない
            assertThat(EffectivePeriod.of(T1, T2).contains(T1.minusNanos(1))).isFalse();
        }

        @Test
        @DisplayName("AC-2 終了ちょうどは含まない")
        void excludesEnd() {
            // 準備：終了のある期間
            var period = EffectivePeriod.of(T1, T2);

            // 確認：終了の時点を含まない
            assertThat(period.contains(T2)).isFalse();

            // 確認：終了の直前までは含む
            assertThat(period.contains(T2.minusNanos(1))).isTrue();
        }

        @Test
        @DisplayName("AC-2 開始が空白なら、過去全てを含む")
        void unboundedStartIncludesPast() {
            // 操作と確認：開始が空白なら、最も古い時点も含む
            assertThat(EffectivePeriod.of(null, T1).contains(Instant.EPOCH)).isTrue();
        }

        @Test
        @DisplayName("AC-2 開始が空白でも、終了ちょうどは含まない")
        void unboundedStartExcludesEnd() {
            // 操作と確認：終了の時点を含まない
            assertThat(EffectivePeriod.of(null, T1).contains(T1)).isFalse();
        }

        @Test
        @DisplayName("AC-2 終了が空白なら、無期限")
        void unboundedEndIncludesFuture() {
            // 操作と確認：終了が空白なら、遠い未来も含む
            assertThat(EffectivePeriod.of(T1, null).contains(FAR_FUTURE)).isTrue();
        }

        @Test
        @DisplayName("AC-2 終了が空白でも、開始より前は含まない")
        void unboundedEndExcludesBeforeStart() {
            // 操作と確認：開始の直前を含まない
            assertThat(EffectivePeriod.of(T1, null).contains(T1.minusNanos(1))).isFalse();
        }

        @Test
        @DisplayName("AC-2 開始・終了とも空白なら、全ての時点を含む")
        void unboundedIncludesAll() {
            // 準備：開始・終了とも空白の期間
            var period = EffectivePeriod.of(null, null);

            // 確認：古い時点も先の時点も含む
            assertThat(period.contains(Instant.EPOCH)).isTrue();
            assertThat(period.contains(T2)).isTrue();
        }
    }

    @Nested
    @DisplayName("開始済み・終了済み")
    class StartedAndEnded {

        @Test
        @DisplayName("AC-5 開始の日時になったら開始済み")
        void hasStartedAtStart() {
            // 準備：開始のある期間
            var period = EffectivePeriod.of(T1, T2);

            // 確認：開始の直前は開始済みでない
            assertThat(period.hasStarted(T1.minusNanos(1))).isFalse();

            // 確認：開始の時点で開始済み
            assertThat(period.hasStarted(T1)).isTrue();
        }

        @Test
        @DisplayName("AC-5 開始が空白なら、常に開始済み")
        void unboundedStartHasAlwaysStarted() {
            // 操作と確認：最も古い時点でも開始済み
            assertThat(EffectivePeriod.of(null, T2).hasStarted(Instant.EPOCH)).isTrue();
        }

        @Test
        @DisplayName("AC-5 終了の日時になったら終了済み")
        void hasEndedAtEnd() {
            // 準備：終了のある期間
            var period = EffectivePeriod.of(T1, T2);

            // 確認：終了の直前は終了済みでない
            assertThat(period.hasEnded(T2.minusNanos(1))).isFalse();

            // 確認：終了の時点で終了済み
            assertThat(period.hasEnded(T2)).isTrue();
        }

        @Test
        @DisplayName("AC-5 終了が空白なら、終了しない")
        void unboundedEndNeverEnds() {
            // 操作と確認：遠い未来でも終了済みでない
            assertThat(EffectivePeriod.of(T1, null).hasEnded(FAR_FUTURE)).isFalse();
        }
    }
}
