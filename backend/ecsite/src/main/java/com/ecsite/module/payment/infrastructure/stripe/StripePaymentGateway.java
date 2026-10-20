package com.ecsite.module.payment.infrastructure.stripe;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.domain.payment.PaymentErrorCode;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.StripeException;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;

/**
 * Stripe の決済サービス（docs/backend/modules/payment.md「Stripe」）。<br />
 * 支払の画面は Checkout Session（与信だけを取る）、与信は PaymentIntent。売上確定は PaymentIntent の capture、返金は Refund。
 * Stripe の型とエラーはこのクラスの外に出さない。
 */
@Slf4j
@RequiredArgsConstructor
class StripePaymentGateway implements PaymentGateway {

    /**
     * 支払の画面の期限（作ってから）。Stripe の最短の 30 分に、時計のずれの余裕を足す
     */
    private static final Duration PAGE_LIFETIME = Duration.ofMinutes(31);

    /**
     * 通貨
     */
    private static final String CURRENCY = "jpy";

    /**
     * 支払の画面の明細の名前（金額は注文の総額の 1 行にする）
     */
    private static final String LINE_ITEM_NAME = "ご注文";

    /**
     * Checkout Session の状態：支払が済んだ
     */
    private static final String SESSION_COMPLETE = "complete";

    /**
     * Checkout Session の状態：期限切れ（閉じた）
     */
    private static final String SESSION_EXPIRED = "expired";

    /**
     * PaymentIntent の状態：取消済み（与信の期限切れでも、Stripe が取り消す）
     */
    private static final String INTENT_CANCELED = "canceled";

    /**
     * PaymentIntent の状態：売上確定済み
     */
    private static final String INTENT_SUCCEEDED = "succeeded";

    /**
     * Stripe のエラーコード：今の状態ではできない（確定済み、取消済みの PaymentIntent の売上確定など）
     */
    private static final String UNEXPECTED_STATE = "payment_intent_unexpected_state";

    /**
     * Stripe のエラーコード：返金済み
     */
    private static final String ALREADY_REFUNDED = "charge_already_refunded";

    /**
     * Stripe の API
     */
    private final StripeClient client;

    /**
     * 時計
     */
    private final Clock clock;

    @Override
    public PaymentPage openPage(PageRequest request) {
        var params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(CURRENCY)
                                .setUnitAmount(request.amount().yen())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(LINE_ITEM_NAME)
                                        .build())
                                .build())
                        .build())
                // 与信だけを取り、売上の確定（capture）はしない
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                        .setCaptureMethod(SessionCreateParams.PaymentIntentData.CaptureMethod.MANUAL)
                        .putMetadata("orderId", request.orderId().toString())
                        .putMetadata("paymentId", request.paymentId().toString())
                        .build())
                .setClientReferenceId(request.orderId().toString())
                .putMetadata("paymentId", request.paymentId().toString())
                // 支払を終えても、やめても、店舗の注文の画面に戻す（結果は通知で受け取る）
                .setSuccessUrl(request.returnUrl())
                .setCancelUrl(request.returnUrl())
                .setExpiresAt(clock.instant().plus(PAGE_LIFETIME).getEpochSecond())
                .build();
        try {
            var session = client.v1().checkout().sessions().create(params);
            return new PaymentPage(session.getId(), session.getUrl());
        } catch (StripeException e) {
            throw failure("支払の画面を作れない: paymentId=" + request.paymentId(), e);
        }
    }

    @Override
    public CloseResult closePage(String pageId) {
        try {
            client.v1().checkout().sessions().expire(pageId);
            return CloseResult.CLOSED;
        } catch (InvalidRequestException e) {
            // 開いていない Session は閉じられない。読み直して、支払が済んでいたか、もう閉じていたかを決める
            return closedState(pageId, e);
        } catch (StripeException e) {
            throw failure("支払の画面を閉じられない: pageId=" + pageId, e);
        }
    }

    @Override
    public void voidAuthorization(String authorizationRef) {
        try {
            client.v1().paymentIntents().cancel(authorizationRef);
        } catch (InvalidRequestException e) {
            // 取消済みの与信は取り消せない。読み直して、取消済みなら成功とする
            if (!isCanceled(authorizationRef)) {
                throw failure("与信を取り消せない: authorizationRef=" + authorizationRef, e);
            }
            log.info("取消済みの与信の取消を、成功として扱う: authorizationRef={}", authorizationRef);
        } catch (StripeException e) {
            throw failure("与信を取り消せない: authorizationRef=" + authorizationRef, e);
        }
    }

    @Override
    public CaptureResult capture(CaptureRequest request) {
        var params = PaymentIntentCaptureParams.builder().setAmountToCapture(request.amount().yen()).build();
        var options = RequestOptions.builder().setIdempotencyKey("capture-" + request.orderId()).build();
        try {
            client.v1().paymentIntents().capture(request.authorizationRef(), params, options);
            return CaptureResult.CAPTURED;
        } catch (InvalidRequestException e) {
            // 確定済みと与信の期限切れ（Stripe が取り消している）は、同じ理由で拒否される。読み直して見分ける
            if (!UNEXPECTED_STATE.equals(e.getCode())) {
                throw failure("売上を確定できない: orderId=" + request.orderId(), e);
            }
            return capturedState(request, e);
        } catch (ApiConnectionException | ApiException | RateLimitException e) {
            log.warn("Stripe に接続できず、売上を確定できない: orderId={}, requestId={}", request.orderId(), e.getRequestId(), e);
            return CaptureResult.UNREACHABLE;
        } catch (StripeException e) {
            throw failure("売上を確定できない: orderId=" + request.orderId(), e);
        }
    }

    @Override
    public void refund(RefundRequest request) {
        var params = RefundCreateParams.builder()
                .setPaymentIntent(request.authorizationRef())
                .setAmount(request.amount().yen())
                .build();
        var options = RequestOptions.builder().setIdempotencyKey("refund-" + request.orderId()).build();
        try {
            client.v1().refunds().create(params, options);
        } catch (InvalidRequestException e) {
            // 返金済みは拒否される。重ねた返金は成功とする
            if (!ALREADY_REFUNDED.equals(e.getCode())) {
                throw failure("返金できない: orderId=" + request.orderId(), e);
            }
            log.info("返金済みの返金を、成功として扱う: orderId={}", request.orderId());
        } catch (StripeException e) {
            throw failure("返金できない: orderId=" + request.orderId(), e);
        }
    }

// ============================== プライベート ==============================

    /**
     * 閉じられなかった Session の状態から、閉じた結果を決める。
     */
    private CloseResult closedState(String pageId, InvalidRequestException rejected) {
        String status;
        try {
            status = client.v1().checkout().sessions().retrieve(pageId).getStatus();
        } catch (StripeException e) {
            throw failure("支払の画面の状態を読めない: pageId=" + pageId, e);
        }
        return switch (status) {
            case SESSION_COMPLETE -> CloseResult.ALREADY_PAID;
            case SESSION_EXPIRED -> CloseResult.CLOSED;
            default -> throw failure("支払の画面を閉じられない: pageId=" + pageId + ", status=" + status, rejected);
        };
    }

    /**
     * 売上確定を拒否された PaymentIntent の状態から、確定の結果を決める。確定済みなら成功、取消済みなら与信期限切れ。
     */
    private CaptureResult capturedState(CaptureRequest request, InvalidRequestException rejected) {
        String status;
        try {
            status = client.v1().paymentIntents().retrieve(request.authorizationRef()).getStatus();
        } catch (StripeException e) {
            throw failure("与信の状態を読めない: authorizationRef=" + request.authorizationRef(), e);
        }
        return switch (status) {
            case INTENT_SUCCEEDED -> CaptureResult.CAPTURED;
            case INTENT_CANCELED -> CaptureResult.AUTHORIZATION_EXPIRED;
            default -> throw failure("売上を確定できない: orderId=" + request.orderId() + ", status=" + status, rejected);
        };
    }

    /**
     * 与信が取消済みか。
     */
    private boolean isCanceled(String authorizationRef) {
        try {
            return INTENT_CANCELED.equals(client.v1().paymentIntents().retrieve(authorizationRef).getStatus());
        } catch (StripeException e) {
            throw failure("与信の状態を読めない: authorizationRef=" + authorizationRef, e);
        }
    }

    /**
     * Stripe の失敗をシステムのエラーにする。Stripe の要求の ID を残し、ダッシュボードの記録と照らせるようにする。<br />
     * 接続できない・Stripe の側の故障・回数の制限は、外部サービスのエラー（やり直せば成功しうる）にする。
     * 認証・権限・要求の誤りは、設定かコードの誤りなので、予期しないエラーのままにする。
     */
    private static SystemException failure(String message, StripeException e) {
        var debugMessage = "Stripe：" + message + ", requestId=" + e.getRequestId();
        if (isUnavailable(e)) {
            return SystemException.of(debugMessage, e, PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }
        return SystemException.of(debugMessage, e);
    }

    /**
     * Stripe が使えない（接続できない・Stripe の側の故障・回数の制限）失敗か。
     */
    private static boolean isUnavailable(StripeException e) {
        return e instanceof ApiConnectionException || e instanceof ApiException || e instanceof RateLimitException;
    }
}
