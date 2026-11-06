package com.ecsite.module.payment.application.port;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.domain.payment.PaymentPage;

import java.time.Instant;
import java.util.UUID;

/**
 * 決済サービスの口（docs/02-設計/payment.md「外部のサービス」）。<br />
 * 実装は mock と Stripe で、mock フラグ {@code ecsite.payment.mock.enabled} が立てば mock、なければ Stripe を使う。決済サービスの型とエラーは実装の外に出さない。
 * 売上確定を除き、どの操作も、決済サービスに接続できなければ（時間内に応えない、決済サービスの側の故障、回数の制限）、
 * 外部サービスのエラー（{@code PAYMENT_GATEWAY_UNAVAILABLE}）の {@link com.ecsite.common.error.exception.SystemException} を投げる。
 * それ以外の失敗（認証・リクエストの誤りなど）は、予期しないエラーの {@link com.ecsite.common.error.exception.SystemException} を投げる。
 */
public interface PaymentGateway {

    /**
     * 与信だけを取る支払画面を作る。同じリクエストで重ねて呼ばれても、決済サービスで支払は 1 つになるようにする。
     *
     * @param request 支払画面のリクエスト
     * @return 作った支払画面
     */
    PaymentPage openPage(PageRequest request);

    /**
     * 支払画面を閉じ、それ以降は支払えないようにする。すでに閉じていれば成功とする。
     *
     * @param pageId 支払画面 ID
     * @return 閉じた結果
     */
    CloseResult closePage(String pageId);

    /**
     * 支払の済んだ支払画面の与信を取得する。支払が済んでいなければ、予期しないエラーの SystemException を投げる。
     * 接続の失敗の扱いは他の操作と同じ。
     *
     * @param pageId 支払画面 ID
     * @return 取得した与信
     */
    Authorization fetchAuthorization(String pageId);

    /**
     * 与信を取り消す。すでに取り消していれば成功とする。<br />
     * 売上を確定した与信は取り消せないので、例外にせず結果で返す。
     *
     * @param authorizationRef 与信の参照
     * @return 与信の取消の結果
     */
    VoidResult voidAuthorization(String authorizationRef);

    /**
     * 与信の総額の売上を確定する。同じ注文で重ねて呼ばれても、決済サービスで売上は 1 回だけ確定するようにする。<br />
     * 与信の期限切れと、決済サービスに接続できないことは、例外にせず結果で返す。
     *
     * @param request 売上確定のリクエスト
     * @return 売上確定の結果
     */
    CaptureResult capture(CaptureRequest request);

    /**
     * 確定した売上を返金する。同じ注文で重ねて呼ばれても、決済サービスで返金は 1 回だけ行うようにする。
     * すでに返金済みなら成功とする。
     *
     * @param request 返金のリクエスト
     */
    void refund(RefundRequest request);

    /**
     * 支払画面のリクエスト
     *
     * @param paymentId 支払 ID（決済サービスでの重複を防ぐ鍵に使う）
     * @param orderId   注文 ID
     * @param amount    金額（与信を取る額）
     * @param returnUrl 支払を終える・やめるときに戻る URL
     */
    record PageRequest(UUID paymentId, UUID orderId, Money amount, String returnUrl) {
    }

    /**
     * 取得した与信
     *
     * @param authorizationRef 与信の参照
     * @param authorizedAt     与信の時刻（決済サービスが返す時刻。実際の与信より早いことがある）
     */
    record Authorization(String authorizationRef, Instant authorizedAt) {
    }

    /**
     * 売上確定のリクエスト
     *
     * @param orderId          注文 ID（決済サービスでの重複を防ぐ鍵に使う）
     * @param authorizationRef 与信の参照
     * @param amount           金額（確定する額）
     */
    record CaptureRequest(UUID orderId, String authorizationRef, Money amount) {
    }

    /**
     * 返金のリクエスト
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
     * 与信の取消の結果
     */
    enum VoidResult {

        /**
         * 取り消した（すでに取り消していた場合を含む）
         */
        VOIDED,

        /**
         * 売上を確定していて、取り消せない。返金で戻す
         */
        ALREADY_CAPTURED,
    }

    /**
     * 支払画面を閉じた結果
     */
    enum CloseResult {

        /**
         * 閉じた（支払は済んでいない）
         */
        CLOSED,

        /**
         * 閉じる前に支払が済んでいた。与信は、通知か、期限切れのジョブの取得で反映する
         */
        ALREADY_PAID,
    }
}
