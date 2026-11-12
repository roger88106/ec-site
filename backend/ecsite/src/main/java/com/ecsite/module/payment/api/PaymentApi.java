package com.ecsite.module.payment.api;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.api.model.AuthorizationDeadline;
import com.ecsite.module.payment.api.model.PaymentSummary;
import com.ecsite.module.payment.application.PaymentCaptureService;
import com.ecsite.module.payment.application.PaymentCommandService;
import com.ecsite.module.payment.application.PaymentPageService;
import com.ecsite.module.payment.application.PaymentQueryService;
import com.ecsite.module.payment.application.model.view.PaymentSummaryView;
import com.ecsite.module.payment.domain.payment.PaymentStatus;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 支払の API
 */
@Component
@RequiredArgsConstructor
public class PaymentApi {

    /**
     * 支払の更新のサービス
     */
    private final PaymentCommandService commandService;

    /**
     * 支払画面のサービス
     */
    private final PaymentPageService pageService;

    /**
     * 売上確定のサービス
     */
    private final PaymentCaptureService captureService;

    /**
     * 支払の参照のサービス
     */
    private final PaymentQueryService queryService;

    /**
     * 注文の支払を未払いで作る。支払の期限は、注文日時に設定の時間を加えた時刻。同じ注文で重ねて呼んでも 1 つ。
     * 呼んだ側のトランザクションに加わり、決済サービスを呼ばない。
     *
     * @param orderId   注文 ID
     * @param amount    金額（注文の総額）
     * @param orderedAt 注文日時
     */
    public void create(@NonNull UUID orderId, @NonNull Money amount, @NonNull Instant orderedAt) {
        commandService.create(orderId, amount, orderedAt);
    }

    /**
     * 支払画面の URL を返す。開いている画面があれば同じ画面を返す。
     * トランザクションの外で呼ばれる前提で、読み取りと更新を別々のトランザクションで行い、その間で決済サービスを呼ぶ。
     *
     * @param orderId 注文 ID
     * @return 支払画面の URL。支払がない、未払いでない、期限の後なら空
     * @throws SystemException 決済サービスに接続できない（PAYMENT_GATEWAY_UNAVAILABLE）
     */
    public Optional<String> pageUrl(@NonNull UUID orderId) {
        return pageService.pageUrl(orderId);
    }

    /**
     * 注文のキャンセルに合わせて支払を取り消す。与信済みなら与信の取消待ちに、売上確定済みなら返金待ちにする。
     * 決済サービスでの取消と返金はコミットの後に行い、その結果を待たない。
     * 呼んだ側のトランザクションに加わり、何度呼んでも結果は同じ。
     *
     * @param orderId 注文 ID
     */
    public void cancel(@NonNull UUID orderId) {
        commandService.cancel(orderId);
    }

    /**
     * 注文の支払の売上を確定する。売上確定済みなら、決済サービスを呼ばずに成功する。
     * トランザクションの外で呼ばれる前提で、読み取りと更新を別々のトランザクションで行い、その間で決済サービスを呼ぶ。
     *
     * @param orderId 注文 ID
     * @throws BusinessException 支払がない（NOT_FOUND）、売上を確定できる状態でない（PAYMENT_NOT_CAPTURABLE）、
     *                           与信の期限が切れている（AUTHORIZATION_EXPIRED）
     * @throws SystemException   決済サービスに接続できない（PAYMENT_GATEWAY_UNAVAILABLE）。支払は与信済みのまま残る
     */
    public void capture(@NonNull UUID orderId) {
        captureService.capture(orderId);
    }

    /**
     * 注文の支払の状態をまとめて返す。
     *
     * @param orderIds 注文 ID
     * @return 支払の状態。支払のない注文は含まない
     */
    public List<PaymentSummary> find(@NonNull Collection<UUID> orderIds) {
        return queryService.findSummaries(orderIds).stream().map(PaymentApi::toSummary).toList();
    }

    /**
     * 支払える（未払いで支払の期限の前の）注文 ID をまとめて返す。
     *
     * @param orderIds 注文 ID
     * @return 支払える注文 ID
     */
    public Set<UUID> findPayableOrderIds(@NonNull Collection<UUID> orderIds) {
        return queryService.findPayableOrderIds(orderIds);
    }

    /**
     * 注文の与信期限と、期限が近いかをまとめて返す。
     *
     * @param orderIds 注文 ID
     * @return 与信期限。与信を受けていない注文は含まない
     */
    public List<AuthorizationDeadline> findAuthorizationDeadlines(@NonNull Collection<UUID> orderIds) {
        return queryService.findAuthorizationDeadlines(orderIds).stream()
                .map(view -> new AuthorizationDeadline(view.orderId(), view.deadline(), view.expiring()))
                .toList();
    }

    /**
     * 与信期限が近い注文 ID を返す。期限を過ぎたものを含む。売上確定の前の注文だけが対象。
     *
     * @return 注文 ID（与信の古い順）
     */
    public List<UUID> findExpiringOrderIds() {
        return queryService.findExpiringOrderIds();
    }

    /**
     * 支払の状態の view を公開の型にする。
     */
    private static PaymentSummary toSummary(PaymentSummaryView view) {
        return new PaymentSummary(view.orderId(), toStatus(view.status()), view.deadline());
    }

    /**
     * 支払の状態の列挙を公開の型にする。
     */
    private static PaymentSummary.Status toStatus(PaymentStatus status) {
        return switch (status) {
            case UNPAID -> PaymentSummary.Status.UNPAID;
            case AUTHORIZED -> PaymentSummary.Status.AUTHORIZED;
            case VOID_PENDING -> PaymentSummary.Status.VOID_PENDING;
            case VOIDED -> PaymentSummary.Status.VOIDED;
            case NOT_PAID -> PaymentSummary.Status.NOT_PAID;
            case CAPTURED -> PaymentSummary.Status.CAPTURED;
            case AUTHORIZATION_EXPIRED -> PaymentSummary.Status.AUTHORIZATION_EXPIRED;
            case REFUND_PENDING -> PaymentSummary.Status.REFUND_PENDING;
            case REFUNDED -> PaymentSummary.Status.REFUNDED;
        };
    }
}
