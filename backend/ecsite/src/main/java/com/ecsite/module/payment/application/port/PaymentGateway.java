package com.ecsite.module.payment.application.port;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.domain.payment.PaymentPage;

import java.util.UUID;

/**
 * 決済サービスの口（docs/backend/modules/payment.md「決済サービス」）。<br />
 * 実装は mock と Stripe で、設定 {@code ecsite.payment.gateway} で切り替える。決済サービスの型とエラーは実装の外に出さない。
 * 売上確定を除き、どの操作も、決済サービスが応えなければ {@link com.ecsite.common.error.exception.SystemException} を投げる。
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
     * 与信の総額の売上を確定する。同じ注文で重ねて呼ばれても、決済サービスで売上は 1 回だけ確定するようにする。<br />
     * 与信の期限切れと、決済サービスに接続できないことは、例外にせず結果で返す。
     *
     * @param request 売上確定の要求
     * @return 売上確定の結果
     */
    CaptureResult capture(CaptureRequest request);

    /**
     * 確定した売上を返金する。同じ注文で重ねて呼ばれても、決済サービスで返金は 1 回だけ行うようにする。
     * すでに返金済みなら成功とする。
     *
     * @param request 返金の要求
     */
    void refund(RefundRequest request);

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
     * 売上確定の要求
     *
     * @param orderId          注文 ID（決済サービスでの重複を防ぐ鍵に使う）
     * @param authorizationRef 与信の参照
     * @param amount           金額（確定する額）
     */
    record CaptureRequest(UUID orderId, String authorizationRef, Money amount) {
    }

    /**
     * 返金の要求
     *
     * @param orderId          注文 ID（決済サービスでの重複を防ぐ鍵に使う）
     * @param authorizationRef 与信の参照
     * @param amount           金額（返金する額）
     */
    record RefundRequest(UUID orderId, String authorizationRef, Money amount) {
    }

    /**
     * 売上確定の結果
     */
    enum CaptureResult {

        /**
         * 売上を確定した（すでに確定していた場合を含む）
         */
        CAPTURED,

        /**
         * 与信の期限が切れていて、売上を確定できない
         */
        AUTHORIZATION_EXPIRED,

        /**
         * 決済サービスに接続できない。売上を確定したかは分からないので、重ねて呼んでよい
         */
        UNREACHABLE,
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
