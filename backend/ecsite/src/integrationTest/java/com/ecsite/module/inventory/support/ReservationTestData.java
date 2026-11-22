package com.ecsite.module.inventory.support;

import com.ecsite.common.model.id.ReservationHolderId;
import com.ecsite.common.model.id.VariantId;
import com.ecsite.module.inventory.api.StockReservationApi;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * 在庫の引当のテストデータ
 *
 * <p>在庫の引当の API で引き当てる。在庫は、呼ぶ側が先に用意する。
 * 引当は呼んだ側のトランザクションに加わる（外から呼ぶと例外）ので、テンプレートのトランザクションの中で呼び、コミットする。
 */
public class ReservationTestData {

    /**
     * 在庫の引当の API
     */
    private final StockReservationApi api;

    /**
     * 引当を包むテンプレート
     */
    private final TransactionTemplate transactionTemplate;

    /**
     * コンストラクタ
     *
     * @param api                 在庫の引当の API
     * @param transactionTemplate 引当を包むテンプレート
     */
    public ReservationTestData(StockReservationApi api, TransactionTemplate transactionTemplate) {
        this.api = api;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 全品目を引き当てる（足りなければ何も引き当てない）。
     *
     * @param holderId   持ち主 ID
     * @param quantities バリアント ID → 数量
     * @param expiresAt  期限
     * @return 足りないバリアントの ID。引き当てたら空
     */
    public Set<VariantId> reserve(ReservationHolderId holderId, Map<VariantId, Integer> quantities, Instant expiresAt) {
        return transactionTemplate.execute(status -> api.reserve(holderId, quantities, expiresAt));
    }
}
