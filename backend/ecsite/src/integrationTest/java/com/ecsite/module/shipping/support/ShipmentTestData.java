package com.ecsite.module.shipping.support;

import com.ecsite.common.model.id.OrderId;
import com.ecsite.module.shipping.api.ShippingApi;
import com.ecsite.module.shipping.api.model.ShipmentSummary;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

/**
 * 出荷のモジュールの出荷のテストデータ
 *
 * <p>注文を通さずに、出荷の API で出荷を作る（注文の出荷済みへの変更と売上の確定はしない）。
 * 注文を通して出荷を登録するときは {@code ShippingTestData} を使う。
 * 出荷の作成は呼んだ側のトランザクションに加わる（外から呼ぶと例外）ので、テンプレートのトランザクションの中で呼び、コミットする。
 */
public class ShipmentTestData {

    /**
     * 出荷の API
     */
    private final ShippingApi shippingApi;

    /**
     * 出荷の作成を包むテンプレート
     */
    private final TransactionTemplate transactionTemplate;

    /**
     * コンストラクタ
     *
     * @param shippingApi         出荷の API
     * @param transactionTemplate 出荷の作成を包むテンプレート
     */
    public ShipmentTestData(ShippingApi shippingApi, TransactionTemplate transactionTemplate) {
        this.shippingApi = shippingApi;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 出荷を作る。
     *
     * @param orderId        注文 ID
     * @param carrier        配送業者
     * @param trackingNumber 送り状番号
     * @param shippedAt      出荷日時
     */
    public void create(OrderId orderId, ShipmentSummary.Carrier carrier, String trackingNumber, Instant shippedAt) {
        transactionTemplate.executeWithoutResult(status -> shippingApi.create(orderId, carrier, trackingNumber, shippedAt));
    }
}
