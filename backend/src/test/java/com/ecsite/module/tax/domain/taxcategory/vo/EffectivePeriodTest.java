package com.ecsite.module.tax.domain.taxcategory.vo;

import com.ecsite.module.tax.domain.taxcategory.TaxErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 適用期間の単体テスト（T102）
 */
class EffectivePeriodTest {

    private static final Instant T1 = Instant.parse("2027-03-31T15:00:00Z");
    private static final Instant T2 = Instant.parse("2028-03-31T15:00:00Z");

    @Nested
    @DisplayName("作成")
    class Create {

        @Test
        @DisplayName("AC-2 開始は終了より前")
        void acceptsStartBeforeEnd() {
            var period = EffectivePeriod.of(T1, T2);

            assertThat(period.effectiveFrom()).isEqualTo(T1);
            assertThat(period.effectiveTo()).isEqualTo(T2);
        }

        @Test
        @DisplayName("AC-2 開始と終了が同じ期間は拒否される")
        void rejectsEmptyPeriod() {
            assertBusinessError(() -> EffectivePeriod.of(T1, T1), TaxErrorCode.INVALID_EFFECTIVE_PERIOD);
        }

        @Test
        @DisplayName("AC-2 開始が終了より後の期間は拒否される")
        void rejectsStartAfterEnd() {
            assertBusinessError(() -> EffectivePeriod.of(T2, T1), TaxErrorCode.INVALID_EFFECTIVE_PERIOD);
        }

        @Test
        @DisplayName("AC-2 開始・終了とも空白にできる")
        void acceptsUnbounded() {
            var period = EffectivePeriod.of(null, null);

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
            assertThat(EffectivePeriod.of(T1, T2).contains(T1)).isTrue();
        }

        @Test
        @DisplayName("AC-2 終了ちょうどは含まない")
        void excludesEnd() {
            var period = EffectivePeriod.of(T1, T2);

            assertThat(period.contains(T2)).isFalse();
            assertThat(period.contains(T2.minusNanos(1))).isTrue();
        }

        @Test
        @DisplayName("AC-2 開始より前は含まない")
        void excludesBeforeStart() {
            assertThat(EffectivePeriod.of(T1, T2).contains(T1.minusNanos(1))).isFalse();
        }

        @Test
        @DisplayName("AC-2 開始が空白なら、過去すべてを含む")
        void unboundedStartIncludesPast() {
            assertThat(EffectivePeriod.of(null, T1).contains(Instant.EPOCH)).isTrue();
            assertThat(EffectivePeriod.of(null, T1).contains(T1)).isFalse();
        }

        @Test
        @DisplayName("AC-2 終了が空白なら、無期限")
        void unboundedEndIncludesFuture() {
            assertThat(EffectivePeriod.of(T1, null).contains(Instant.parse("9999-12-31T00:00:00Z"))).isTrue();
            assertThat(EffectivePeriod.of(T1, null).contains(T1.minusNanos(1))).isFalse();
        }

        @Test
        @DisplayName("AC-2 開始・終了とも空白なら、すべての時点を含む")
        void unboundedIncludesAll() {
            var period = EffectivePeriod.of(null, null);

            assertThat(period.contains(Instant.EPOCH)).isTrue();
            assertThat(period.contains(T2)).isTrue();
        }
    }

    @Nested
    @DisplayName("開始済み・終了済み")
    class StartedAndEnded {

        @Test
        @DisplayName("AC-5 開始の日時になったら開始済み。開始が空白なら常に開始済み")
        void hasStarted() {
            assertThat(EffectivePeriod.of(T1, T2).hasStarted(T1.minusNanos(1))).isFalse();
            assertThat(EffectivePeriod.of(T1, T2).hasStarted(T1)).isTrue();
            assertThat(EffectivePeriod.of(null, T2).hasStarted(Instant.EPOCH)).isTrue();
        }

        @Test
        @DisplayName("AC-5 終了の日時になったら終了済み。終了が空白なら終了しない")
        void hasEnded() {
            assertThat(EffectivePeriod.of(T1, T2).hasEnded(T2.minusNanos(1))).isFalse();
            assertThat(EffectivePeriod.of(T1, T2).hasEnded(T2)).isTrue();
            assertThat(EffectivePeriod.of(T1, null).hasEnded(Instant.parse("9999-12-31T00:00:00Z"))).isFalse();
        }
    }
}
