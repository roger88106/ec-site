package com.ecsite.module.payment.application;

import com.ecsite.common.id.IdGenerator;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.application.model.Authorization;
import com.ecsite.module.payment.application.model.view.PaymentCaptureView;
import com.ecsite.module.payment.application.port.PaymentNotificationReceiver;
import com.ecsite.module.payment.domain.payment.Payment;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import com.ecsite.module.payment.domain.payment.PaymentStatus;
import com.ecsite.module.payment.domain.payment.port.PaymentRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

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
     * 識別子の生成
     */
    private final IdGenerator idGenerator;

    /**
     * 注文の支払を未払いで作る。
     *
     * <p>同じ注文の支払がすでにあれば何もしない。呼んだ側のトランザクションに加わる（トランザクションの外から呼ぶと例外）。
     *
     * @param orderId   注文 ID
     * @param amount    金額（注文の総額）
     * @param orderedAt 注文日時（支払の期限の起点）
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void create(@NonNull UUID orderId, @NonNull Money amount, @NonNull Instant orderedAt) {
        var payment = Payment.create(idGenerator.generate(), orderId, amount, orderedAt.plus(properties.deadline()));
        paymentRepository.insertIfAbsent(payment);
    }

    /**
     * 決済サービスで開いた支払画面を記録する。
     *
     * <p>先に記録した画面があれば、その画面を返す。
     *
     * @param orderId 注文 ID
     * @param opened  開いた支払画面
     * @return 使う支払画面。支払えなければ空
     */
    public Optional<PaymentPage> recordPage(@NonNull UUID orderId, @NonNull PaymentPage opened) {
        return paymentRepository.lockByOrderId(orderId)
                // 行ロックの待ちで期限を過ぎた支払に記録しないよう、時刻は行ロックの後に取る
                .filter(payment -> payment.isPayable(clock.instant()))
                // 同時のリクエストが先に記録した画面があれば、それを使う
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
            // 変えたときだけ保存する
            if (payment.authorize(authorizationRef, clock.instant())) {
                paymentRepository.update(payment);
            }
        }, () -> log.warn("知らない支払画面の与信の成功を無視する: pageId={}", pageId));
    }

    /**
     * 決済サービスから取得した与信を反映する。
     *
     * <p>行ロックの後に確認し直す。変えたときだけ保存する。
     *
     * @param paymentId     支払 ID
     * @param authorization 取得した与信
     * @return 与信済みにしたら true（先に通知が届いていれば false。支払なし（NOT_PAID）の支払に反映して与信の取消待ちにしたときも false）
     */
    public boolean authorizeFromGateway(@NonNull UUID paymentId, @NonNull Authorization authorization) {
        var now = clock.instant();
        return paymentRepository.lockById(paymentId).map(payment -> {
            var changed = payment.authorize(authorization.authorizationRef(), authorization.authorizedAt(), now);
            if (changed) {
                paymentRepository.update(payment);
            }
            // 変えた後が与信済みなら、変える前は未払いだった
            return changed && payment.status() == PaymentStatus.AUTHORIZED;
        }).orElse(false);
    }

    /**
     * 期限を過ぎた未払いの支払を、支払なしにする。
     *
     * <p>期限の前の支払と、未払いでない支払は変えない。
     * 未払いの支払に、読んだときになかった支払画面が行ロックの後にあれば、開いた画面が残るのを避けるために、
     * その回は支払を変えない（次の回のジョブが、その画面を読んで閉じる）。
     *
     * @param paymentId  支払 ID
     * @param readPageId 読んだときの支払画面 ID。なければ null
     */
    public void expire(@NonNull UUID paymentId, @Nullable String readPageId) {
        var now = clock.instant();
        paymentRepository.lockById(paymentId).ifPresent(payment -> {
            // 未払いでなければ、次の回の検索は読まないので、見送りにしない
            var unpaid = payment.status() == PaymentStatus.UNPAID;
            // 支払画面は一度記録したら変わらないので、画面があって読んだ画面と違えば、読んだ後に記録された
            var pageRecordedAfterRead = payment.page().filter(page -> !page.id().equals(readPageId)).isPresent();
            if (unpaid && pageRecordedAfterRead) {
                log.info("読んだ後に支払画面が記録されたので、この回は支払を変えない（次の回で扱う）: paymentId={}", paymentId);
                return;
            }
            expireIfPastDeadline(payment, now);
        });
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
        modify(paymentId, payment -> payment.awaitAuthorization(now));
    }

    /**
     * 決済サービスに売上確定を求める前に、注文の支払を読み、求めた時刻を記録する。
     *
     * <p>注文の支払を行ロックして読み、与信済みなら求めた時刻を記録して、1 つのトランザクションでコミットする。
     * 読みと記録の間に、支払の状態が変わることがある隙間を作らない。決済サービスを呼ぶ前にコミットするので、
     * 決済サービスで売上を確定した後にインスタンスが止まっても、時刻が残る。
     * 与信済みでなければ、時刻を記録せず、読んだ状態を返す。
     *
     * @param orderId 注文 ID
     * @return 売上確定に使う支払（状態は、行ロックで読んだ時点の値）。支払がなければ空
     */
    public Optional<PaymentCaptureView> requestCapture(@NonNull UUID orderId) {
        return paymentRepository.lockByOrderId(orderId).map(payment -> {
            // 行ロックの待ちで早まらないよう、時刻は行ロックの後に取る
            if (payment.requestCapture(clock.instant())) {
                paymentRepository.update(payment);
            }
            return new PaymentCaptureView(payment.id(), payment.status(), payment.authorizationRef(), payment.amount());
        });
    }

    /**
     * 決済サービスで売上を確定したことを反映する。
     *
     * <p>与信済みなら売上確定済みに、与信の取消待ちなら返金待ちにする。変えたときだけ保存する。
     *
     * @param paymentId 支払 ID
     * @return 変えた後の状態（売上確定済み、または返金待ち）。変えなければ（すでに売上確定済みなど）空
     */
    public Optional<PaymentStatus> markCaptured(@NonNull UUID paymentId) {
        var now = clock.instant();
        return paymentRepository.lockById(paymentId).filter(payment -> payment.capture(now)).map(payment -> {
            paymentRepository.update(payment);
            return payment.status();
        });
    }

    /**
     * 決済サービスで売上を確定していなかったとき、売上確定を求めた時刻を削除する。
     *
     * <p>行ロックの後の時刻が、照合で読んだときの時刻と同じときだけ削除する。照合が決済サービスを読む間に
     * 売上確定が再実行されて時刻が新しくなっていれば、その再実行の結果はまだ分からないので、削除しない。
     *
     * @param paymentId       支払 ID
     * @param readRequestedAt 照合で読んだ売上確定を求めた時刻
     * @return 削除したら true
     */
    public boolean clearCaptureRequest(@NonNull UUID paymentId, @NonNull Instant readRequestedAt) {
        return modify(paymentId, payment -> payment.clearCaptureRequest(readRequestedAt));
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
     * <p>決済サービスでの取消と返金、支払画面を閉じる操作は、コミットの後に行う。
     * 支払がなければ何もしない。呼んだ側のトランザクションに加わる（トランザクションの外から呼ぶと例外）。
     *
     * @param orderId 注文 ID
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancel(@NonNull UUID orderId) {
        // 支払のない注文（決済を入れる前の注文）は、取り消すものがない
        paymentRepository.lockByOrderId(orderId).ifPresentOrElse(payment -> {
            // 変えたときだけ保存する
            if (payment.cancel(clock.instant())) {
                paymentRepository.update(payment);
            }
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
     * 支払を行ロックして操作を 1 つ呼び、変えたときだけ保存する。
     *
     * <p>支払がなければ何もしない。
     *
     * @param paymentId 支払 ID
     * @param operation 支払への操作。変えたら true を返す
     * @return 変えて保存したら true
     */
    private boolean modify(UUID paymentId, Predicate<Payment> operation) {
        return paymentRepository.lockById(paymentId).filter(operation).map(payment -> {
            paymentRepository.update(payment);
            return true;
        }).orElse(false);
    }
}
