package com.ecsite.common.model.value;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.ZoneId;

/**
 * 業務の時間帯
 *
 * <p>業務の日付は、この時間帯で切る。時刻は UTC で持ち、日付にするときだけこの時間帯を使う。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BusinessTime {

    /**
     * 業務の時間帯（日本時間）
     */
    public static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
}
