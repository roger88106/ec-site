package com.ecsite.module.payment.application;

import com.ecsite.module.payment.application.port.PaymentNotificationReceiver;
import com.ecsite.module.payment.domain.payment.Payment;
import com.ecsite.module.payment.domain.payment.port.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 決済サービスの通知のサービス
 *
 * <p>支払を行ロックしてから反映する。重ねて届いた通知と、順序の入れ替わった通知は、支払の状態の遷移で無視される。
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
class PaymentNotificationService implements PaymentNotificationReceiver {

    /**
     * 支払のリポジトリ
     */
    private final PaymentRepository paymentRepository;

    /**
     * 支払の更新のサービス
     */
    private final PaymentCommandService commandService;

    /**
     * 時計
     */
    private final Clock clock;

    /**
     * 与信の成功を反映する。知らない支払画面なら、記録して無視する。
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
     * 支払画面の期限切れを反映する。支払の期限の後で未払いなら、支払なしにする。
     *
     * @param pageId 支払画面 ID
     */
    @Override
    public void pageExpired(String pageId) {
        // 支払なしにする処理は、期限切れのジョブと同じ更新のサービスのメソッドで行う
        // （同じトランザクションで行ロックし直す。期限の前の通知では何もしない）
        paymentRepository.lockByPageId(pageId)
                .map(Payment::id)
                .ifPresent(commandService::expire);
    }
}
