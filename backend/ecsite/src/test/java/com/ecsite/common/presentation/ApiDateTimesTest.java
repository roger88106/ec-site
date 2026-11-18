package com.ecsite.common.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API の日時の変換の単体テスト
 */
class ApiDateTimesTest {

    @Test
    @DisplayName("API の日時を、マイクロ秒未満を切り捨てたアプリの日時にする")
    void truncatesBelowMicrosecond() {
        // 準備：ナノ秒まで持つ API の日時
        var dateTime = OffsetDateTime.parse("2099-01-01T00:00:00.123456789Z");

        // 操作と確認：マイクロ秒未満（789 ナノ秒）が切り捨てられる
        assertThat(ApiDateTimes.toInstant(dateTime)).isEqualTo(Instant.parse("2099-01-01T00:00:00.123456Z"));
    }

    @Test
    @DisplayName("API の日時が null なら null にする")
    void returnsNullForNull() {
        // 操作と確認：null のまま
        assertThat(ApiDateTimes.toInstant(null)).isNull();
    }
}
