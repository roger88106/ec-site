package com.ecsite.module.inventory.infrastructure.persistence;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jooq.Condition;

import java.time.Instant;
import java.util.UUID;

import static com.ecsite.jooq.inventory.Tables.STOCK_RESERVATION;

/**
 * 有効な引当の条件。引当のリポジトリと、販売可能数の照会で同じ条件を使う。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ActiveReservations {

    /**
     * 期限が切れておらず、除く持ち主のものでない引当。期限ちょうどは切れたものとする。
     *
     * @param excludedHolderId 除く持ち主。なければ null
     * @param now              現在時刻
     * @return 条件
     */
    static Condition condition(UUID excludedHolderId, Instant now) {
        var active = STOCK_RESERVATION.EXPIRES_AT.gt(now);
        return excludedHolderId == null ? active : active.and(STOCK_RESERVATION.HOLDER_ID.ne(excludedHolderId));
    }
}
