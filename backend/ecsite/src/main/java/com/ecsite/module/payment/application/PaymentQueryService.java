package com.ecsite.module.payment.application;

import com.ecsite.module.payment.application.model.view.AuthorizationDeadlineView;
import com.ecsite.module.payment.application.model.view.PastDeadlinePaymentView;
import com.ecsite.module.payment.application.model.view.PayablePaymentView;
import com.ecsite.module.payment.application.model.view.PaymentCaptureView;
import com.ecsite.module.payment.application.model.view.PaymentSummaryView;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.application.port.PaymentQueryRepository;
import com.ecsite.module.payment.domain.payment.Payment;
import com.ecsite.module.payment.domain.payment.PaymentStatus;
import com.ecsite.module.payment.domain.payment.port.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 支払の参照のサービス
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PaymentQueryService {

    /**
     * 支払のクエリリポジトリ
     */
    private final PaymentQueryRepository queryRepository;

    /**
     * 支払のリポジトリ
     */
    private final PaymentRepository paymentRepository;

    /**
     * 決済の設定
     */
    private final PaymentProperties properties;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 注文の支払の状態をまとめて取得する。
     *
     * @param orderIds 注文 ID
     * @return 支払の状態。支払のない注文は含まない
     */
    public List<PaymentSummaryView> findSummaries(Collection<UUID> orderIds) {
        return queryRepository.findSummaries(orderIds);
    }

    /**
     * 注文の与信期限と、期限が近いかをまとめて取得する。
     *
     * @param orderIds 注文 ID
     * @return 与信期限。与信を受けていない注文は含まない
     */
    public List<AuthorizationDeadlineView> findAuthorizationDeadlines(Collection<UUID> orderIds) {
        var term = properties.authorizationTerm();
        var now = clock.instant();
        return queryRepository.findAuthorizations(orderIds).stream()
                .map(view -> new AuthorizationDeadlineView(
                        view.orderId(),
                        term.deadlineOf(view.authorizedAt()),
                        view.status().watchesAuthorizationDeadline() && term.isExpiring(view.authorizedAt(), now)))
                .toList();
    }

    /**
     * 与信期限が近い注文 ID を、与信の古い順に取得する。期限を過ぎたものを含む。
     *
     * @return 注文 ID
     */
    public List<UUID> findExpiringOrderIds() {
        var latest = properties.authorizationTerm().latestExpiringAuthorizedAt(clock.instant());
        return queryRepository.findOrderIdsAuthorizedUntil(latest);
    }

    /**
     * 支払える支払を取得する（未払いで期限の前）。
     *
     * @param orderId 注文 ID
     * @return 支払。支払えなければ空
     */
    public Optional<PayablePaymentView> findPayable(UUID orderId) {
        var now = clock.instant();
        return paymentRepository.findByOrderId(orderId)
                .filter(payment -> payment.isPayable(now))
                .map(payment -> new PayablePaymentView(payment.id(), payment.amount(), payment.page().orElse(null)));
    }

    /**
     * 売上を確定する前の注文の支払を取得する。
     *
     * @param orderId 注文 ID
     * @return 支払。なければ空
     */
    public Optional<PaymentCaptureView> findForCapture(UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(payment -> new PaymentCaptureView(payment.id(), payment.status(), payment.authorizationRef(),
                        payment.amount()));
    }

    /**
     * 期限を過ぎた未払いの支払を、期限の古い順に取得する。
     *
     * @param limit 取得する数の上限
     * @return 支払
     */
    public List<PastDeadlinePaymentView> findPastDeadline(int limit) {
        return paymentRepository.findUnpaidPastDeadline(clock.instant(), limit).stream()
                .map(payment -> new PastDeadlinePaymentView(payment.id(), payment.page().orElse(null)))
                .toList();
    }

    /**
     * 決済サービスで取り消す与信の参照を返す。
     *
     * @param paymentId 支払 ID
     * @return 与信の参照。与信の取消待ちでなければ空（取消済みなど）
     */
    public Optional<String> findAuthorizationToVoid(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .filter(payment -> payment.status() == PaymentStatus.VOID_PENDING)
                .map(Payment::authorizationRef);
    }

    /**
     * 決済サービスに求める返金の要求を返す。
     *
     * @param paymentId 支払 ID
     * @return 返金の要求。返金待ちでなければ空（返金済みなど）
     */
    public Optional<PaymentGateway.RefundRequest> findRefundRequest(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .filter(payment -> payment.status() == PaymentStatus.REFUND_PENDING)
                .map(payment -> new PaymentGateway.RefundRequest(
                        payment.orderId(), payment.authorizationRef(), payment.amount()));
    }
}
