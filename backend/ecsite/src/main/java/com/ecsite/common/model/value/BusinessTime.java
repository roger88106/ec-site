package com.ecsite.common.model.value;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.ZoneId;

/**
 * 業務の時間帯。<br />
 * 業務の日付（注文番号の日付、年齢の基準日、注文の一覧の日付の範囲、売上レポートの日）は、この時間帯で切る。
 * 時刻は UTC で持ち（docs/02-設計/backend-architecture.md 第 6.1 節）、日付にするときだけこの時間帯を使う。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BusinessTime {

    /**
     * 業務の時間帯（日本時間）
     */
    public static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
}
