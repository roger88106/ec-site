package com.ecsite.module.payment.domain.payment.port;

import com.ecsite.module.payment.domain.payment.Payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 支払のリポジトリ
 */
public interface PaymentRepository {

    /**
     * 新しい支払を保存する。同じ注文の支払がすでにあれば、何もせずに false を返す。トランザクションは壊さない。
     *
     * @param payment 支払
     * @return 保存したら true
     */
    boolean insertIfAbsent(Payment payment);

    /**
     * 支払の変更（状態、支払の画面、与信の参照）を保存し、登録されたイベントを発行する。バージョンを上げる。
     *
     * @param payment 変更した支払
     */
    void update(Payment payment);

    /**
     * 支払を行ロックして取得する。同じ支払への通知と操作を 1 つずつ処理するため。トランザクションの中で呼ぶ。
     *
     * @param id 支払 ID
     * @return 支払。なければ空
     */
    Optional<Payment> lockById(UUID id);

    /**
     * 注文の支払を行ロックして取得する。トランザクションの中で呼ぶ。
     *
     * @param orderId 注文 ID
     * @return 支払。なければ空
     */
    Optional<Payment> lockByOrderId(UUID orderId);

    /**
     * 支払の画面の支払を行ロックして取得する。決済サービスの通知は画面の ID で届く。トランザクションの中で呼ぶ。
     *
     * @param pageId 支払の画面 ID
     * @return 支払。なければ空
     */
    Optional<Payment> lockByPageId(String pageId);

    /**
     * 支払を取得する。
     *
     * @param id 支払 ID
     * @return 支払。なければ空
     */
    Optional<Payment> findById(UUID id);

    /**
     * 注文の支払を取得する。
     *
     * @param orderId 注文 ID
     * @return 支払。なければ空
     */
    Optional<Payment> findByOrderId(UUID orderId);

    /**
     * 期限を過ぎた未払いの支払を、期限の古い順に取得する。
     *
     * @param now   現在時刻
     * @param limit 取得する数の上限
     * @return 支払
     */
    List<Payment> findUnpaidPastDeadline(Instant now, int limit);
}
