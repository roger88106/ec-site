package com.ecsite.module.payment.application;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.application.model.Authorization;
import com.ecsite.module.payment.application.port.PaymentNotificationReceiver;
import com.ecsite.module.payment.domain.payment.Payment;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import com.ecsite.module.payment.domain.payment.port.PaymentRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 支払の更新のサービス
 *
 * <p>決済サービスを呼ばず、DB の中の操作だけを行う。外部の応答を待つ間に、行ロックを持たないため。
 * 決済サービスの通知も、支払を行ロックしてから反映する。重ねて届いた通知と、順序の入れ替わった通知は、支払の状態の遷移で無視される。
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PaymentCommandService implements PaymentNotificationReceiver {

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
     * 注文の支払を未払いで作る。
     *
     * <p>同じ注文の支払がすでにあれば何もしない。呼んだ側のトランザクションに加わる。
     *
     * @param orderId   注文 ID
     * @param amount    金額（注文の総額）
     * @param orderedAt 注文日時（支払の期限の起点）
     */
    public void create(@NonNull UUID orderId, @NonNull Money amount, @NonNull Instant orderedAt) {
        var payment = Payment.create(UUID.randomUUID(), orderId, amount, orderedAt.plus(properties.deadline()));
        paymentRepository.insertIfAbsent(payment);
    }

    /**
     * 決済サービスで開いた支払画面を覚える。
     *
     * <p>先に覚えた画面があれば、その画面を返す。
     *
     * @param orderId 注文 ID
     * @param opened  開いた支払画面
     * @return 使う支払画面。支払えなければ空
     */
    public Optional<PaymentPage> recordPage(@NonNull UUID orderId, @NonNull PaymentPage opened) {
        var now = clock.instant();
        return paymentRepository.lockByOrderId(orderId)
                .filter(payment -> payment.isPayable(now))
                // 同時のリクエストが先に覚えた画面があれば、それを使う
                .map(payment -> payment.page().orElseGet(() -> {
                    payment.openPage(opened);
                    paymentRepository.update(payment);
                    return opened;
                }));
    }

    /**
     * 決済サービスの通知で、与信の成功を反映する。
     *
     * <p>知らない支払画面なら、ログに残して無視する。
     *
     * @param pageId           支払画面 ID
     * @param authorizationRef 与信の参照
     */
    @Override
    public void authorized(String pageId, String authorizationRef) {
        paymentRepository.lockByPageId(pageId).ifPresentOrElse(payment -> {
            payment.authorize(authorizationRef, clock.instant());
            paymentRepository.update(payment);
        }, () -> log.warn("知らない支払画面の与信の成功を無視する: pageId={}", pageId));
    }

    /**
     * 決済サービスから取得した与信を反映する。
     *
     * <p>行ロックの後に確認し直す。
     *
     * @param paymentId     支払 ID
     * @param authorization 取得した与信
     * @return 未払いから与信済みにしたら true（先に通知が届いていれば false）
     */
    public boolean authorizeFromGateway(@NonNull UUID paymentId, @NonNull Authorization authorization) {
        var now = clock.instant();
        return paymentRepository.lockById(paymentId).map(payment -> {
            var authorized = payment.authorize(authorization.authorizationRef(), authorization.authorizedAt(), now);
            paymentRepository.update(payment);
            return authorized;
        }).orElse(false);
    }

    /**
     * 期限を過ぎた未払いの支払を、支払なしにする。
     *
     * <p>期限の前の支払と、未払いでない支払は変えない。
     *
     * @param paymentId 支払 ID
     */
    public void expire(@NonNull UUID paymentId) {
        var now = clock.instant();
        paymentRepository.lockById(paymentId).ifPresent(payment -> expireIfPastDeadline(payment, now));
    }

    /**
     * 決済サービスの通知で、支払画面の期限切れを反映する。
     *
     * <p>支払の期限の後で未払いなら、支払なしにする。期限の前の通知では何もしない。
     *
     * @param pageId 支払画面 ID
     */
    @Override
    public void pageExpired(String pageId) {
        var now = clock.instant();
        paymentRepository.lockByPageId(pageId).ifPresent(payment -> expireIfPastDeadline(payment, now));
    }

    /**
     * 期限を過ぎたが決済サービスで支払が済んでいた支払を、与信の反映を待つ支払にする。
     *
     * @param paymentId 支払 ID
     */
    public void awaitAuthorization(@NonNull UUID paymentId) {
        var now = clock.instant();
        // 行ロックの後に確認し直し、読んでから通知が届いて与信済みになった支払を変えない
        paymentRepository.lockById(paymentId)
                .filter(payment -> payment.awaitAuthorization(now))
                .ifPresent(paymentRepository::update);
    }

    /**
     * 決済サービスで売上を確定したことを反映する。
     *
     * @param paymentId 支払 ID
     */
    public void markCaptured(@NonNull UUID paymentId) {
        modify(paymentId, payment -> payment.capture(clock.instant()));
    }

    /**
     * 売上確定が与信の期限切れで断られたことを反映する。
     *
     * @param paymentId 支払 ID
     */
    public void markAuthorizationExpired(@NonNull UUID paymentId) {
        modify(paymentId, Payment::expireAuthorization);
    }

    /**
     * 注文のキャンセルに合わせて支払を取り消す。
     *
     * <p>決済サービスでの取消と返金は、コミットの後に行う。
     * 支払がなければ何もしない。呼んだ側のトランザクションに加わる。
     *
     * @param orderId 注文 ID
     */
    public void cancel(@NonNull UUID orderId) {
        // 支払のない注文（決済を入れる前の注文）は、取り消すものがない
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
    public void markVoided(@NonNull UUID paymentId) {
        modify(paymentId, Payment::markVoided);
    }

    /**
     * 決済サービスで返金したことを反映する。
     *
     * @param paymentId 支払 ID
     */
    public void markRefunded(@NonNull UUID paymentId) {
        modify(paymentId, Payment::markRefunded);
    }

// ============================== プライベートメソッド ==============================

    /**
     * 行ロックした支払が期限を過ぎた未払いなら、支払なしにして保存する。
     *
     * <p>行ロックの後に確認し直し、読んでから与信済みになった支払と、期限の前の支払を変えない。
     *
     * @param payment 行ロックした支払
     * @param now     現在時刻
     */
    private void expireIfPastDeadline(Payment payment, Instant now) {
        if (!payment.isPayable(now) && payment.expire(now)) {
            paymentRepository.update(payment);
        }
    }

    /**
     * 支払を行ロックして操作を 1 つ呼び、保存する。
     *
     * <p>支払がなければ何もしない。
     *
     * @param paymentId 支払 ID
     * @param operation 支払への操作
     */
    private void modify(UUID paymentId, Consumer<Payment> operation) {
        paymentRepository.lockById(paymentId).ifPresent(payment -> {
            operation.accept(payment);
            paymentRepository.update(payment);
        });
    }
}
