package com.ecsite.module.payment.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 与信の有効期間の単体テスト
 *
 * <p>有効期間が正で、期限が近いとする長さが 0 以上かつ有効期間より短いことを、作るときに検査することを確認する。
 */
class AuthorizationTermTest {

    // ============================== テスト ==============================

    @ParameterizedTest(name = "有効期間 {0}、近いとする長さ {1}")
    @CsvSource({
            // 有効期間が正の下限（1ns）で、近いとする長さが 0
            "PT0.000000001S, PT0S",
            // 近いとする長さが、有効期間の 1ns 前
            "PT168H, PT167H59M59.999999999S",
            // 今の設定の既定値（7 日、2 日）
            "PT168H, PT48H"})
    @DisplayName("有効期間が正で、近いとする長さが 0 以上かつ有効期間より短ければ、作れる")
    void createsValidTerm(Duration validity, Duration warning) {
        // 操作：与信の有効期間を作る
        var term = new AuthorizationTerm(validity, warning);

        // 確認：渡した値を持つ
        assertThat(term.validity()).isEqualTo(validity);
        assertThat(term.warning()).isEqualTo(warning);
    }

    @ParameterizedTest(name = "有効期間 {0}")
    @CsvSource({"PT0S", "PT-0.000000001S"})
    @DisplayName("有効期間が 0 か負なら、作れない")
    void rejectsNonPositiveValidity(Duration validity) {
        // 操作と確認：例外になる
        assertThatThrownBy(() -> new AuthorizationTerm(validity, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("近いとする長さが負なら、作れない")
    void rejectsNegativeWarning() {
        // 操作と確認：-1ns は例外になる
        assertThatThrownBy(() -> new AuthorizationTerm(Duration.ofDays(7), Duration.ofNanos(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "近いとする長さ {0}")
    @CsvSource({"PT168H", "PT168H0.000000001S"})
    @DisplayName("近いとする長さが有効期間と同じか長ければ、作れない")
    void rejectsWarningNotShorterThanValidity(Duration warning) {
        // 操作と確認：有効期間 7 日に対して、7 日と 7 日 ＋ 1ns は例外になる
        assertThatThrownBy(() -> new AuthorizationTerm(Duration.ofDays(7), warning))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
