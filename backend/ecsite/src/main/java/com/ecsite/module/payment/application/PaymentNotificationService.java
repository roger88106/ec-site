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
 * 決済サービスからの通知を、支払に反映するサービス。<br />
 * 支払を行ロックしてから反映する。重ねて届いた通知・順序の入れ替わった通知は、支払の状態の遷移で無視される。
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PaymentNotificationService implements PaymentNotificationReceiver {

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
     * 与信の成功を反映する。知らない支払の画面なら、記録して無視する。
     *
     * @param pageId           支払の画面 ID
     * @param authorizationRef 与信の参照
     */
    @Override
    public void authorized(String pageId, String authorizationRef) {
        paymentRepository.lockByPageId(pageId).ifPresentOrElse(payment -> {
            payment.authorize(authorizationRef, clock.instant());
            paymentRepository.update(payment);
        }, () -> log.warn("知らない支払の画面の与信の成功を無視する: pageId={}", pageId));
    }

    /**
     * 支払の画面の期限切れを反映する。支払の期限の後で未払いなら、支払なしにする。<br />
     * 期限の前の通知（起こらないはず）は無視し、期限切れのジョブに任せる。
     *
     * @param pageId 支払の画面 ID
     */
    @Override
    public void pageExpired(String pageId) {
        // 期限切れにする処理は、期限切れのジョブと同じ更新の service のメソッドに任せる（同じトランザクションで行ロックし直す）
        paymentRepository.lockByPageId(pageId)
                .map(Payment::id)
                .ifPresent(commandService::expire);
    }
}
