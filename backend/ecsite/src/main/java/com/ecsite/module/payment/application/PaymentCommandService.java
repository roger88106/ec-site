package com.ecsite.module.payment.application;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.domain.payment.Payment;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import com.ecsite.module.payment.domain.payment.port.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 支払の更新のサービス。決済サービスを呼ばない、DB の中の操作だけを持つ。<br />
 * 決済サービスの呼び出しはトランザクションの外で行う（{@link PaymentPageService} など）。外部の応答を待つ間、行ロックを持たないため。
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PaymentCommandService {

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
     * 注文の支払を未払いで作る。同じ注文の支払がすでにあれば何もしない。<br />
     * 呼び出し元（注文の成立）のトランザクションに参加する。
     *
     * @param orderId   注文 ID
     * @param amount    金額（注文の総額）
     * @param orderedAt 注文日時（支払の期限の起点）
     */
    public void create(UUID orderId, Money amount, Instant orderedAt) {
        var payment = Payment.create(UUID.randomUUID(), orderId, amount, orderedAt.plus(properties.deadline()));
        paymentRepository.insertIfAbsent(payment);
    }

    /**
     * 支払える支払を取得する（未払いで期限の前）。
     *
     * @param orderId 注文 ID
     * @return 支払。支払えなければ空
     */
    @Transactional(readOnly = true)
    public Optional<Payment> findPayable(UUID orderId) {
        var now = clock.instant();
        return paymentRepository.findByOrderId(orderId).filter(payment -> payment.isPayable(now));
    }

    /**
     * 期限を過ぎた未払いの支払を、期限の古い順に取得する。
     *
     * @param limit 取得する数の上限
     * @return 支払
     */
    @Transactional(readOnly = true)
    public List<Payment> findPastDeadline(int limit) {
        return paymentRepository.findUnpaidPastDeadline(clock.instant(), limit);
    }

    /**
     * 期限を過ぎた未払いの支払を、支払なしにする。<br />
     * 行ロックの後に確かめ直すので、その間に与信済みになった支払や、期限の前の支払は変えない。
     *
     * @param paymentId 支払 ID
     */
    public void expire(UUID paymentId) {
        var now = clock.instant();
        paymentRepository.lockById(paymentId)
                .filter(payment -> !payment.isPayable(now))
                .filter(payment -> payment.expire(now))
                .ifPresent(paymentRepository::update);
    }

    /**
     * 決済サービスで開いた支払の画面を覚える。<br />
     * ほかの要求が先に画面を覚えていれば、その画面を返す。支払えなくなっていれば空を返す（呼ぶ側が開いた画面を閉じる）。
     *
     * @param orderId 注文 ID
     * @param opened  開いた支払の画面
     * @return 使う支払の画面。支払えなければ空
     */
    public Optional<PaymentPage> recordPage(UUID orderId, PaymentPage opened) {
        var now = clock.instant();
        return paymentRepository.lockByOrderId(orderId)
                .filter(payment -> payment.isPayable(now))
                .map(payment -> {
                    if (payment.page().isPresent()) {
                        return payment.page().get();
                    }
                    payment.openPage(opened);
                    paymentRepository.update(payment);
                    return opened;
                });
    }
}
