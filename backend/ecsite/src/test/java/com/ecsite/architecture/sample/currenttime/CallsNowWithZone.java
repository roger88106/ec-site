package com.ecsite.architecture.sample.currenttime;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 現在時刻の取り方の検査の例：時間帯だけを渡して now を呼ぶ（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class CallsNowWithZone {

    /**
     * 例の関数。
     *
     * @return 今の時刻
     */
    LocalDate now() {
        return LocalDate.now(ZoneId.of("Asia/Tokyo"));
    }
}
