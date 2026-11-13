package com.ecsite.module.payment.infrastructure.stripe;

import com.ecsite.common.error.exception.SystemException;
import com.ecsite.module.payment.application.port.PaymentGateway;
import com.ecsite.module.payment.domain.PaymentErrorCode;
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
import com.stripe.param.checkout.SessionRetrieveParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Stripe の決済サービス
 *
 * <p>支払画面は Checkout Session（与信だけを取る）、与信は PaymentIntent。売上確定は PaymentIntent の capture、返金は Refund。
 * Stripe の型とエラーはこのクラスの外に出さない。
 */
@Slf4j
@RequiredArgsConstructor
class StripePaymentGateway implements PaymentGateway {

    /**
     * 支払画面の期限（作ってから）。Stripe の最短の 30 分に、時計のずれの余裕を追加する。支払の期限の上限（30 分）は、この値を前提にする
     */
    private static final Duration PAGE_LIFETIME = Duration.ofMinutes(31);

    /**
     * 通貨
     */
    private static final String CURRENCY = "jpy";

    /**
     * 支払画面の明細の名前（金額は注文の総額の 1 行にする）
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

    /**
     * {@inheritDoc}
     *
     * <p>Stripe の冪等の鍵は付けない。同じ鍵では、期限の切れた Session も 24 時間返り続けるため。
     * 重ねて作った画面は、呼んだ側が閉じる。
     */
    @Override
    public PaymentPage openPage(PageRequest request) {
        var params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                // 明細：注文の総額の 1 行
                .addLineItem(totalLineItem(request))
                // 与信だけを取る PaymentIntent
                .setPaymentIntentData(authorizationOnly(request))
                // Stripe のダッシュボードで、Session を注文と支払に照合するための ID
                .setClientReferenceId(request.orderId().toString())
                .putMetadata("paymentId", request.paymentId().toString())
                // 戻る先：支払を終えても、やめても、店舗の注文の画面に戻す（結果は通知で受け取る）
                .setSuccessUrl(request.returnUrl())
                .setCancelUrl(request.returnUrl())
                // 期限：過ぎると Stripe が画面を閉じ、期限切れを通知する
                .setExpiresAt(clock.instant().plus(PAGE_LIFETIME).getEpochSecond())
                .build();
        try {
            var session = client.v1().checkout().sessions().create(params);
            return new PaymentPage(session.getId(), session.getUrl());
        } catch (StripeException e) {
            throw failure("支払画面を作れない: paymentId=" + request.paymentId(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloseResult closePage(String pageId) {
        try {
            client.v1().checkout().sessions().expire(pageId);
            return CloseResult.CLOSED;
        } catch (InvalidRequestException e) {
            // 開いていない Session は閉じられない。読み直して、支払が済んでいたか、もう閉じていたかを決める
            return closeResultOf(pageId, e);
        } catch (StripeException e) {
            throw failure("支払画面を閉じられない: pageId=" + pageId, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Authorization fetchAuthorization(String pageId) {
        var params = SessionRetrieveParams.builder().addExpand("payment_intent").build();
        try {
            var session = client.v1().checkout().sessions().retrieve(pageId, params);
            var intent = session.getPaymentIntentObject();
            if (!SESSION_COMPLETE.equals(session.getStatus()) || intent == null || intent.getCreated() == null) {
                throw SystemException.of("Stripe：支払の済んだ支払画面ではないので、与信を取得できない: pageId=" + pageId
                        + ", status=" + session.getStatus() + ", paymentIntent=" + (intent != null), null);
            }
            return new Authorization(intent.getId(), Instant.ofEpochSecond(intent.getCreated()));
        } catch (StripeException e) {
            throw failure("与信を取得できない: pageId=" + pageId, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VoidResult voidAuthorization(String authorizationRef) {
        try {
            client.v1().paymentIntents().cancel(authorizationRef);
            return VoidResult.VOIDED;
        } catch (InvalidRequestException e) {
            // 取消済みと売上確定済みの与信は取り消せない。状態を再取得して見分ける
            return voidResultOf(authorizationRef, e);
        } catch (StripeException e) {
            throw failure("与信を取り消せない: authorizationRef=" + authorizationRef, e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Stripe の失敗は、次のように結果に変える。
     * <ul>
     *   <li>今の状態ではできない（確定済み、与信の期限切れ）：PaymentIntent を読み直して、確定済みか期限切れかを決める</li>
     *   <li>Stripe が使えない（接続できない、Stripe の側の故障、回数の制限）：接続できない</li>
     *   <li>それ以外：例外</li>
     * </ul>
     */
    @Override
    public CaptureResult capture(CaptureRequest request) {
        var params = PaymentIntentCaptureParams.builder().setAmountToCapture(request.amount().yen()).build();
        var options = RequestOptions.builder().setIdempotencyKey("capture-" + request.orderId()).build();
        try {
            client.v1().paymentIntents().capture(request.authorizationRef(), params, options);
            return CaptureResult.CAPTURED;
        } catch (InvalidRequestException e) {
            // 確定済みと与信の期限切れ（Stripe が取り消している）は、同じ理由で拒否される
            if (UNEXPECTED_STATE.equals(e.getCode())) {
                return captureResultOf(request, e);
            }
            throw failure("売上を確定できない: orderId=" + request.orderId(), e);
        } catch (StripeException e) {
            if (isUnavailable(e)) {
                log.warn("Stripe に接続できず、売上を確定できない: orderId={}, requestId={}", request.orderId(), e.getRequestId(), e);
                return CaptureResult.UNREACHABLE;
            }
            throw failure("売上を確定できない: orderId=" + request.orderId(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
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
     * 支払画面の明細（注文の総額の 1 行）を作る。
     */
    private static SessionCreateParams.LineItem totalLineItem(PageRequest request) {
        var product = SessionCreateParams.LineItem.PriceData.ProductData.builder()
                .setName(LINE_ITEM_NAME)
                .build();
        var price = SessionCreateParams.LineItem.PriceData.builder()
                .setCurrency(CURRENCY)
                .setUnitAmount(request.amount().yen())
                .setProductData(product)
                .build();
        return SessionCreateParams.LineItem.builder()
                .setQuantity(1L)
                .setPriceData(price)
                .build();
    }

    /**
     * 与信だけを取り、売上を確定（capture）しない PaymentIntent の設定を作る。
     *
     * <p>Session の metadata は PaymentIntent に引き継がれないので、PaymentIntent にも注文と支払の ID を付ける。
     */
    private static SessionCreateParams.PaymentIntentData authorizationOnly(PageRequest request) {
        return SessionCreateParams.PaymentIntentData.builder()
                .setCaptureMethod(SessionCreateParams.PaymentIntentData.CaptureMethod.MANUAL)
                .putMetadata("orderId", request.orderId().toString())
                .putMetadata("paymentId", request.paymentId().toString())
                .build();
    }

    /**
     * 閉じられなかった Session の状態から、閉じた結果を決める。
     */
    private CloseResult closeResultOf(String pageId, InvalidRequestException rejected) {
        String status;
        try {
            status = client.v1().checkout().sessions().retrieve(pageId).getStatus();
        } catch (StripeException e) {
            throw failure("支払画面の状態を読めない: pageId=" + pageId, e);
        }
        return switch (status) {
            case SESSION_COMPLETE -> CloseResult.ALREADY_PAID;
            case SESSION_EXPIRED -> CloseResult.CLOSED;
            default -> throw failure("支払画面を閉じられない: pageId=" + pageId + ", status=" + status, rejected);
        };
    }

    /**
     * 売上確定を拒否された PaymentIntent の状態から、確定の結果を決める。
     *
     * <p>確定済みなら成功、取消済みなら与信期限切れ。
     */
    private CaptureResult captureResultOf(CaptureRequest request, InvalidRequestException rejected) {
        var status = intentStatus(request.authorizationRef());
        return switch (status) {
            case INTENT_SUCCEEDED -> CaptureResult.CAPTURED;
            case INTENT_CANCELED -> CaptureResult.AUTHORIZATION_EXPIRED;
            default -> throw failure("売上を確定できない: orderId=" + request.orderId() + ", status=" + status, rejected);
        };
    }

    /**
     * 取消を拒否された PaymentIntent の状態から、取消の結果を決める。
     *
     * <p>取消済みなら成功、売上確定済みなら返金に進める。
     */
    private VoidResult voidResultOf(String authorizationRef, InvalidRequestException rejected) {
        var status = intentStatus(authorizationRef);
        return switch (status) {
            case INTENT_CANCELED -> {
                log.info("取消済みの与信の取消を、成功として扱う: authorizationRef={}", authorizationRef);
                yield VoidResult.VOIDED;
            }
            case INTENT_SUCCEEDED -> {
                log.warn("売上確定済みの与信の取消を、返金に進める: authorizationRef={}", authorizationRef);
                yield VoidResult.ALREADY_CAPTURED;
            }
            default -> throw failure("与信を取り消せない: authorizationRef=" + authorizationRef + ", status=" + status, rejected);
        };
    }

    /**
     * 与信の PaymentIntent の状態を読む。
     */
    private String intentStatus(String authorizationRef) {
        try {
            return client.v1().paymentIntents().retrieve(authorizationRef).getStatus();
        } catch (StripeException e) {
            throw failure("与信の状態を読めない: authorizationRef=" + authorizationRef, e);
        }
    }

    /**
     * Stripe の失敗をシステムのエラーにする。
     *
     * <p>接続できない・Stripe の側の故障・回数の制限は外部サービスのエラーに、
     * それ以外は予期しないエラーにする。
     */
    private static SystemException failure(String message, StripeException e) {
        // Stripe のリクエストの ID を残し、ダッシュボードの記録と照合できるようにする
        var debugMessage = "Stripe：" + message + ", requestId=" + e.getRequestId();
        // やり直せば成功しうる失敗だけを、外部サービスのエラーにする（認証・権限・リクエストの誤りは、設定かコードの誤り）
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
