package com.ecsite.common.presentation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * API の日時（{@link OffsetDateTime}）と、アプリの日時（{@link Instant}）の変換。<br />
 * API の応答の日時は UTC で返す（画面が日本時間に直して表示する）。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApiDateTimes {

    /**
     * アプリの日時を、API の日時（UTC）にする。
     *
     * @param instant 日時。null なら null
     * @return API の日時
     */
    public static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    /**
     * API の日時を、アプリの日時にする。
     *
     * @param dateTime API の日時。null なら null
     * @return アプリの日時
     */
    public static Instant toInstant(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant();
    }
}
