package com.ecsite.module.payment.application.port;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.domain.payment.PaymentPage;

import java.util.UUID;

/**
 * 決済サービスの口（docs/backend/modules/payment.md「決済サービス」）。<br />
 * 実装は mock と Stripe で、設定 {@code ecsite.payment.gateway} で切り替える。決済サービスの型とエラーは実装の外に出さない。
 * どの操作も、決済サービスが応えなければ {@link com.ecsite.common.error.exception.SystemException} を投げる。
 */
public interface PaymentGateway {

    /**
     * 与信だけを取る支払の画面を作る。同じ要求で重ねて呼ばれても、決済サービスで支払は 1 つになるようにする。
     *
     * @param request 支払の画面の要求
     * @return 作った支払の画面
     */
    PaymentPage openPage(PageRequest request);

    /**
     * 支払の画面を閉じ、それ以降は支払えないようにする。すでに閉じていれば成功とする。
     *
     * @param pageId 支払の画面 ID
     * @return 閉じた結果
     */
    CloseResult closePage(String pageId);

    /**
     * 与信を取り消す。すでに取り消していれば成功とする。
     *
     * @param authorizationRef 与信の参照
     */
    void voidAuthorization(String authorizationRef);

    /**
     * 支払の画面の要求
     *
     * @param paymentId 支払 ID（決済サービスでの重複を防ぐ鍵に使う）
     * @param orderId   注文 ID
     * @param amount    金額（与信を取る額）
     * @param returnUrl 支払を終える・やめるときに戻る URL
     */
    record PageRequest(UUID paymentId, UUID orderId, Money amount, String returnUrl) {
    }

    /**
     * 支払の画面を閉じた結果
     */
    enum CloseResult {

        /**
         * 閉じた（支払は済んでいない）
         */
        CLOSED,

        /**
         * 閉じる前に支払が済んでいた。与信の成功の通知で与信済みになる
         */
        ALREADY_PAID,
    }
}
