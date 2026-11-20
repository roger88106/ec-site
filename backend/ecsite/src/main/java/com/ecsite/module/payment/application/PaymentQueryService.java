package com.ecsite.module.payment.application;

import com.ecsite.module.payment.application.model.view.AuthorizationDeadlineView;
import com.ecsite.module.payment.application.model.view.AwaitingAuthorizationPaymentView;
import com.ecsite.module.payment.application.model.view.CaptureReconcileView;
import com.ecsite.module.payment.application.model.view.PastDeadlinePaymentView;
import com.ecsite.module.payment.application.model.view.PayablePaymentView;
import com.ecsite.module.payment.application.model.view.PaymentSummaryView;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.application.port.PaymentQueryRepository;
import com.ecsite.module.payment.domain.payment.Payment;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import com.ecsite.module.payment.domain.payment.PaymentStatus;
import com.ecsite.module.payment.domain.payment.port.PaymentRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    public List<PaymentSummaryView> findSummaries(@NonNull Collection<UUID> orderIds) {
        return queryRepository.findSummaries(orderIds);
    }

    /**
     * 注文の与信期限と、期限が近いかをまとめて取得する。
     *
     * @param orderIds 注文 ID
     * @return 与信期限。与信を受けていない注文は含まない
     */
    public List<AuthorizationDeadlineView> findAuthorizationDeadlines(@NonNull Collection<UUID> orderIds) {
        var term = properties.authorizationTerm();
        var now = clock.instant();
        // 期限と近いかの判定は、支払の集約に任せる
        return paymentRepository.findByOrderIds(orderIds).stream()
                .flatMap(payment -> payment.authorizationDeadline(term).stream()
                        .map(deadline -> new AuthorizationDeadlineView(
                                payment.orderId(), deadline, payment.isAuthorizationExpiring(term, now))))
                .toList();
    }

    /**
     * 与信期限が近い注文 ID を、与信の古い順に取得する。
     *
     * <p>期限を過ぎたものを含む。
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
    public Optional<PayablePaymentView> findPayable(@NonNull UUID orderId) {
        var now = clock.instant();
        return paymentRepository.findByOrderId(orderId)
                .filter(payment -> payment.isPayable(now))
                .map(payment -> new PayablePaymentView(payment.id(), payment.amount(), payment.page().orElse(null)));
    }

    /**
     * 支払える（未払いで期限の前の）支払の注文 ID をまとめて取得する。
     *
     * @param orderIds 注文 ID
     * @return 支払える注文 ID
     */
    public Set<UUID> findPayableOrderIds(@NonNull Collection<UUID> orderIds) {
        var now = clock.instant();
        return paymentRepository.findByOrderIds(orderIds).stream()
                .filter(payment -> payment.isPayable(now))
                .map(Payment::orderId)
                .collect(Collectors.toSet());
    }

    /**
     * 売上確定の結果が分からない支払を、売上確定を求めた時刻・支払 ID の順に取得する。
     *
     * <p>状態が与信済みで、売上確定を求めてから決済の設定
     * {@code ecsite.payment.capture-reconcile-delay} の時間が過ぎた支払。
     *
     * @param limit 取得する数の上限
     * @return 支払
     */
    public List<CaptureReconcileView> findForCaptureReconcile(int limit) {
        return queryRepository.findCaptureRequestedUntil(clock.instant().minus(properties.captureReconcileDelay()), limit);
    }

    /**
     * 与信の反映を長く待つ未払いの支払の数を返す。
     *
     * <p>待ち始めてから決済の設定
     * {@code ecsite.payment.awaiting-authorization-warning} を過ぎたものを数える。
     *
     * @return 支払の数
     */
    public long countLongAwaitingAuthorization() {
        return queryRepository.countUnpaidAwaitingAuthorizationUntil(
                clock.instant().minus(properties.awaitingAuthorizationWarning()));
    }

    /**
     * 期限を過ぎた未払いの支払を、期限・支払 ID の順に取得する。
     *
     * <p>与信の反映を待つ支払は除く。支払（集約）に復元しないので、行が支払の不変条件を満たさなくても失敗しない。
     *
     * @param limit 取得する数の上限
     * @return 支払
     */
    public List<PastDeadlinePaymentView> findPastDeadline(int limit) {
        return queryRepository.findUnpaidPastDeadline(clock.instant(), limit);
    }

    /**
     * 与信の反映を待つ支払を、待ち始めた時刻・支払 ID の順に取得する。
     *
     * <p>支払（集約）に復元しないので、行が支払の不変条件を満たさなくても失敗しない。
     *
     * @param limit 取得する数の上限
     * @return 支払
     */
    public List<AwaitingAuthorizationPaymentView> findAwaitingAuthorization(int limit) {
        return queryRepository.findUnpaidAwaitingAuthorization(limit);
    }

    /**
     * 決済サービスで取り消す与信の参照を返す。
     *
     * @param paymentId 支払 ID
     * @return 与信の参照。与信の取消待ちでなければ空（取消済みなど）
     */
    public Optional<String> findAuthorizationToVoid(@NonNull UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .filter(payment -> payment.status() == PaymentStatus.VOID_PENDING)
                .map(Payment::authorizationRef);
    }

    /**
     * 決済サービスで閉じる支払画面の ID を返す。
     *
     * @param paymentId 支払 ID
     * @return 支払画面 ID。支払なしでない、または支払画面がなければ空
     */
    public Optional<String> findPageToClose(@NonNull UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .filter(payment -> payment.status() == PaymentStatus.NOT_PAID)
                .flatMap(Payment::page)
                .map(PaymentPage::id);
    }

    /**
     * 決済サービスに求める返金のリクエストを返す。
     *
     * @param paymentId 支払 ID
     * @return 返金のリクエスト。返金待ちでなければ空（返金済みなど）
     */
    public Optional<PaymentGateway.RefundRequest> findRefundRequest(@NonNull UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .filter(payment -> payment.status() == PaymentStatus.REFUND_PENDING)
                .map(payment -> new PaymentGateway.RefundRequest(
                        payment.orderId(),
                        Objects.requireNonNull(payment.authorizationRef(), "返金待ちの支払には与信の参照がある"),
                        payment.amount()));
    }
}
