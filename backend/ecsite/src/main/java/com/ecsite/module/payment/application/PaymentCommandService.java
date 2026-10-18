package com.ecsite.module.payment.application;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.domain.payment.Payment;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import com.ecsite.module.payment.domain.payment.port.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 支払の更新のサービス。決済サービスを呼ばない、DB の中の操作だけを持つ。<br />
 * 決済サービスの呼び出しはトランザクションの外で行う（{@link PaymentPageService} など）。外部の応答を待つ間、行ロックを持たないため。
 */
@Slf4j
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
     * 決済サービスで売上を確定したことを反映する。
     *
     * @param paymentId 支払 ID
     */
    public void markCaptured(UUID paymentId) {
        paymentRepository.lockById(paymentId).ifPresent(payment -> {
            payment.capture(clock.instant());
            paymentRepository.update(payment);
        });
    }

    /**
     * 売上確定が与信の期限切れで断られたことを反映する。
     *
     * @param paymentId 支払 ID
     */
    public void markAuthorizationExpired(UUID paymentId) {
        paymentRepository.lockById(paymentId).ifPresent(payment -> {
            payment.expireAuthorization();
            paymentRepository.update(payment);
        });
    }

    /**
     * 期限を過ぎた未払いの支払を、支払なしにする。期限切れのジョブと、支払の画面の期限切れの通知から呼ぶ。<br />
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
     * 注文のキャンセルに合わせて支払を取り消す。与信済みなら与信の取消待ちに、売上確定済みなら返金待ちにし、
     * コミットの後に決済サービスで取り消す・返金する。与信期限切れなら何もしない。<br />
     * 呼び出し元（注文のキャンセル）のトランザクションに参加する。支払のない注文（決済を入れる前の注文）なら何もしない。
     *
     * @param orderId 注文 ID
     */
    public void cancel(UUID orderId) {
        paymentRepository.lockByOrderId(orderId).ifPresentOrElse(payment -> {
            payment.cancel(clock.instant());
            paymentRepository.update(payment);
        }, () -> log.info("支払のない注文の取消：何もしない: orderId={}", orderId));
    }

    /**
     * 決済サービスで与信を取り消したことを反映する。
     *
     * @param paymentId 支払 ID
     */
    public void markVoided(UUID paymentId) {
        paymentRepository.lockById(paymentId).ifPresent(payment -> {
            payment.markVoided();
            paymentRepository.update(payment);
        });
    }

    /**
     * 決済サービスで返金したことを反映する。
     *
     * @param paymentId 支払 ID
     */
    public void markRefunded(UUID paymentId) {
        paymentRepository.lockById(paymentId).ifPresent(payment -> {
            payment.markRefunded();
            paymentRepository.update(payment);
        });
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
