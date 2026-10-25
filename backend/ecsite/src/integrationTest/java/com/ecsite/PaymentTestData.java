package com.ecsite;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.api.PaymentApi;
import com.ecsite.module.payment.api.PaymentSummary;
import com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 統合テストで、支払の API とテスト用の決済サービスを使い、支払を作り、与信済み・売上確定済みにし、状態を読む補助。<br />
 * 支払の金額は 6,940 円で固定する。支払は注文ごとに別の ID で作る。
 */
public class PaymentTestData {

    /**
     * 支払の金額
     */
    private static final Money AMOUNT = Money.of(6940);

    /**
     * 支払の API
     */
    private final PaymentApi paymentApi;

    /**
     * テスト用の決済サービス
     */
    private final TestPaymentGateway gateway;

    /**
     * 注文 ID → 支払画面 ID（支払の後は画面の URL を引けないため、覚えておく）
     */
    private final Map<UUID, String> pageIds = new HashMap<>();

    /**
     * コンストラクタ
     *
     * @param paymentApi 支払の API
     * @param gateway    テスト用の決済サービス
     */
    public PaymentTestData(PaymentApi paymentApi, TestPaymentGateway gateway) {
        this.paymentApi = paymentApi;
        this.gateway = gateway;
    }

    /**
     * 未払いの支払を、新しい注文 ID で作る。
     *
     * @param orderedAt 注文日時
     * @return 注文 ID
     */
    public UUID created(Instant orderedAt) {
        var orderId = UUID.randomUUID();
        paymentApi.create(orderId, AMOUNT, orderedAt);
        return orderId;
    }

    /**
     * 支払画面を開き、画面の ID を返す。
     *
     * @param orderId 注文 ID
     * @return 支払画面 ID
     */
    public String openPage(UUID orderId) {
        var url = paymentApi.pageUrl(orderId).orElseThrow();
        return url.substring(url.lastIndexOf('/') + 1);
    }

    /**
     * 支払画面を開いて支払い、与信済みにする。画面の ID は覚えておく。
     *
     * @param orderId 注文 ID
     * @return 支払画面 ID
     */
    public String pay(UUID orderId) {
        var pageId = openPage(orderId);
        pageIds.put(orderId, pageId);
        gateway.pay(pageId);
        return pageId;
    }

    /**
     * 未払いの支払を作り、支払画面を開いて支払い、与信済みにする。
     *
     * @param orderedAt 注文日時
     * @return 注文 ID
     */
    public UUID authorized(Instant orderedAt) {
        var orderId = created(orderedAt);
        pay(orderId);
        return orderId;
    }

    /**
     * 与信済みにして、売上を確定する。
     *
     * @param orderedAt 注文日時
     * @return 注文 ID
     */
    public UUID captured(Instant orderedAt) {
        var orderId = authorized(orderedAt);
        paymentApi.capture(orderId);
        return orderId;
    }

    /**
     * 支払い済みにした注文の、支払画面 ID を返す。
     *
     * @param orderId 注文 ID
     * @return 支払画面 ID
     */
    public String pageIdOf(UUID orderId) {
        return pageIds.get(orderId);
    }

    /**
     * 支払の状態を読む。
     *
     * @param orderId 注文 ID
     * @return 支払の状態
     */
    public PaymentSummary.Status statusOf(UUID orderId) {
        return paymentApi.find(List.of(orderId)).getFirst().status();
    }
}
