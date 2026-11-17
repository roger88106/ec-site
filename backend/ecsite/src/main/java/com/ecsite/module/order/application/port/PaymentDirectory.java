package com.ecsite.module.order.application.port;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.order.application.model.PaymentAuthorization;
import com.ecsite.module.order.application.model.PaymentSnapshot;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 決済の腐敗防止層の port
 */
public interface PaymentDirectory {

    /**
     * 注文の支払（未払い）を作る。
     *
     * <p>呼んだ側のトランザクションに加わる。
     *
     * @param orderId   注文 ID
     * @param amount    金額（総額）
     * @param orderedAt 注文日時（支払の期限の起点）
     */
    void create(UUID orderId, Money amount, Instant orderedAt);

    /**
     * 決済サービスを呼んで、支払画面の URL を返す。
     *
     * @param orderId 注文 ID
     * @return 支払画面の URL。支払えなければ空
     */
    Optional<String> pageUrl(UUID orderId);

    /**
     * 売上確定済みでなければ決済サービスを呼んで、注文の支払の売上を確定する。
     *
     * <p>売上確定済みなら、呼ばずに成功する。
     *
     * @param orderId 注文 ID
     * @throws BusinessException 与信の期限が切れている（{@code AUTHORIZATION_EXPIRED}）
     * @throws BusinessException 売上を確定できる状態でない（{@code PAYMENT_NOT_CAPTURABLE}）
     */
    void capture(UUID orderId);

    /**
     * 注文の支払を取り消す。
     *
     * <p>呼んだ側のトランザクションに加わり、与信の取消・返金の結果は待たない。
     *
     * @param orderId 注文 ID
     */
    void cancel(UUID orderId);

    /**
     * 注文の支払をまとめて返す。
     *
     * @param orderIds 注文 ID
     * @return 注文 ID → 支払。支払のない注文（決済を入れる前の注文）は含まない
     */
    Map<UUID, PaymentSnapshot> find(Collection<UUID> orderIds);

    /**
     * 注文の与信期限と、期限が近いかをまとめて返す。
     *
     * @param orderIds 注文 ID
     * @return 注文 ID → 与信期限。与信を受けていない注文は含まない
     */
    Map<UUID, PaymentAuthorization> findAuthorizations(Collection<UUID> orderIds);

    /**
     * 与信期限が近い注文 ID を返す。
     *
     * <p>期限を過ぎたものを含む。売上確定の前の注文だけが対象。
     *
     * @return 注文 ID
     */
    List<UUID> findExpiringOrderIds();
}
