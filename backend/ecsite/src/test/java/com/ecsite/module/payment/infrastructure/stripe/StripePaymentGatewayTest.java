package com.ecsite.module.payment.infrastructure.stripe;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.application.PaymentProperties;
import com.ecsite.module.payment.application.model.Authorization;
import com.ecsite.module.payment.application.port.PaymentGateway.CaptureRequest;
import com.ecsite.module.payment.application.port.PaymentGateway.CaptureResult;
import com.ecsite.module.payment.application.port.PaymentGateway.CaptureState;
import com.ecsite.module.payment.application.port.PaymentGateway.CloseResult;
import com.ecsite.module.payment.application.port.PaymentGateway.PageRequest;
import com.ecsite.module.payment.application.port.PaymentGateway.RefundRequest;
import com.ecsite.module.payment.application.port.PaymentGateway.VoidResult;
import com.ecsite.module.payment.domain.PaymentErrorCode;
import com.ecsite.module.payment.domain.payment.PaymentAmountRange;
import com.ecsite.module.payment.domain.payment.PaymentPage;
import com.ecsite.module.payment.infrastructure.stripe.StripeApiStub.Request;
import org.hibernate.validator.constraints.time.DurationMax;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.ecsite.support.BusinessErrorAssertions.assertSystemError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stripe の実装の単体テスト
 *
 * <p>支払画面の作成・閉じる、与信の取得・取消、売上確定、返金と、失敗の分類を確認する。
 * Stripe の API の代わりに手元の HTTP サーバー（{@link StripeApiStub}）を立て、sandbox で記録した応答の形を返す。
 */
class StripePaymentGatewayTest {

    // ============================== 定数 ==============================

    /**
     * 注文の ID
     */
    private static final UUID ORDER_ID = UUID.fromString("0c4b7f7e-2f2c-4a59-9a39-8a0b6a0f0001");

    /**
     * Stripe の PaymentIntent の ID
     */
    private static final String INTENT_ID = "pi_test_capture";

    /**
     * 支払の ID
     */
    private static final UUID PAYMENT_ID = UUID.fromString("0c4b7f7e-2f2c-4a59-9a39-8a0b6a0f0002");

    /**
     * Stripe の支払画面（Checkout Session）の ID
     */
    private static final String SESSION_ID = "cs_test_page";

    /**
     * Stripe の支払画面の URL
     */
    private static final String SESSION_URL = "https://checkout.stripe.com/c/pay/cs_test_page";

    /**
     * 支払の後に利用者を戻す URL
     */
    private static final String RETURN_URL = "http://localhost:3000/orders/" + ORDER_ID + "?payment=returned";

    /**
     * 総額（円）
     */
    private static final long AMOUNT_YEN = 6940;

    /**
     * 支払画面の期限（作成から 31 分）
     */
    private static final Duration PAGE_LIFETIME = Duration.ofMinutes(31);

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-14 10:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-14T01:00:00Z");

    /**
     * 返金に成功した応答
     */
    private static final String REFUND_SUCCEEDED = """
            {"id": "re_test", "object": "refund", "status": "succeeded", "amount": 6940}""";

    // ============================== 対象 ==============================

    /**
     * Stripe の API の代わりの HTTP サーバー
     */
    private StripeApiStub stripe;

    /**
     * 確認する実装
     */
    private StripePaymentGateway gateway;

    @BeforeEach
    void setUp() throws IOException {
        stripe = new StripeApiStub();
        gateway = new StripePaymentGateway(stripe.client(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        stripe.close();
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("売上確定")
    class Capture {

        @Test
        @DisplayName("Stripe で確定できたら、確定済み")
        void captures() {
            // 準備：確定に成功する
            respond("POST /v1/payment_intents/" + INTENT_ID + "/capture", 200, intent("succeeded"));

            // 操作と確認：確定済み
            assertThat(gateway.capture(captureRequest())).isEqualTo(CaptureResult.CAPTURED);
        }

        @Test
        @DisplayName("PaymentIntent を総額で確定し、冪等キーを付ける")
        void sendsCaptureRequest() {
            // 準備：確定に成功する
            respond("POST /v1/payment_intents/" + INTENT_ID + "/capture", 200, intent("succeeded"));

            // 操作：売上を確定する
            gateway.capture(captureRequest());

            // 確認：総額と、冪等キーが付いていること
            var request = requests().getFirst();
            assertThat(request.body()).contains("amount_to_capture=" + AMOUNT_YEN);
            assertThat(request.idempotencyKey()).isNotBlank();
        }

        @Test
        @DisplayName("AC-616-1 5xx で失敗した後の再実行は、別の冪等キーで確定し、確定済みになる")
        void retriesWithDifferentKeyAfterServerError() {
            // 準備：1 回目は Stripe の側の故障
            respond("POST /v1/payment_intents/" + INTENT_ID + "/capture", 500, StripeApiStub.error("api_error", null));
            assertThat(gateway.capture(captureRequest())).isEqualTo(CaptureResult.UNREACHABLE);

            // 準備：2 回目までに Stripe が回復する
            respond("POST /v1/payment_intents/" + INTENT_ID + "/capture", 200, intent("succeeded"));

            // 操作と確認：2 回目は確定済みで、冪等キーが 1 回目と異なる
            assertThat(gateway.capture(captureRequest())).isEqualTo(CaptureResult.CAPTURED);
            assertDifferentIdempotencyKeys();
        }

        @Test
        @DisplayName("AC-616-3 1 回目が完了していて応答だけ 5xx のとき、再実行で断られても（状態 succeeded）、別の冪等キーで確定済みになる")
        void treatsUnexpectedStateAfterServerErrorAsCaptured() {
            // 準備：1 回目は 5xx
            respond("POST /v1/payment_intents/" + INTENT_ID + "/capture", 500, StripeApiStub.error("api_error", null));
            assertThat(gateway.capture(captureRequest())).isEqualTo(CaptureResult.UNREACHABLE);

            // 準備：2 回目は、確定済みのため断られ、再取得すると確定済み
            respond("POST /v1/payment_intents/" + INTENT_ID + "/capture", 400,
                    StripeApiStub.error("invalid_request_error", "payment_intent_unexpected_state"));
            respond("GET /v1/payment_intents/" + INTENT_ID, 200, intent("succeeded"));

            // 操作と確認：2 回目は確定済みで、冪等キーが 1 回目と異なる
            assertThat(gateway.capture(captureRequest())).isEqualTo(CaptureResult.CAPTURED);
            assertDifferentIdempotencyKeys();
        }

        @Test
        @DisplayName("NFR-616-2 stripe-java の自動の再試行で 5xx の後に成功したら、同じ冪等キーで 2 回送り、確定済みになる")
        void automaticRetrySendsSameKey() {
            // 準備：自動の再試行を 1 回にし、1 回目は 5xx、2 回目は成功
            var retrying = new StripePaymentGateway(stripe.clientWithRetries(1), Clock.fixed(NOW, ZoneOffset.UTC));
            stripe.respondOnce("POST /v1/payment_intents/" + INTENT_ID + "/capture", 500, StripeApiStub.error("api_error", null));
            respond("POST /v1/payment_intents/" + INTENT_ID + "/capture", 200, intent("succeeded"));

            // 操作と確認：確定済みで、1 回の呼び出しで送られた 2 つの POST が同じ冪等キー
            assertThat(retrying.capture(captureRequest())).isEqualTo(CaptureResult.CAPTURED);
            assertSameIdempotencyKeyInOneCall();
        }

        @Test
        @DisplayName("確定済みで断られたら（状態 succeeded）、成功とする")
        void alreadyCaptured() {
            // 準備：確定は断られ、読み直すと確定済み
            respond("POST /v1/payment_intents/" + INTENT_ID + "/capture", 400,
                    StripeApiStub.error("invalid_request_error", "payment_intent_unexpected_state"));
            respond("GET /v1/payment_intents/" + INTENT_ID, 200, intent("succeeded"));

            // 操作と確認：確定済み
            assertThat(gateway.capture(captureRequest())).isEqualTo(CaptureResult.CAPTURED);
        }

        @Test
        @DisplayName("与信が切れて断られたら（状態 canceled）、与信期限切れ")
        void authorizationExpired() {
            // 準備：確定は断られ、読み直すと取り消されている
            respond("POST /v1/payment_intents/" + INTENT_ID + "/capture", 400,
                    StripeApiStub.error("invalid_request_error", "payment_intent_unexpected_state"));
            respond("GET /v1/payment_intents/" + INTENT_ID, 200, intent("canceled"));

            // 操作と確認：与信期限切れ
            assertThat(gateway.capture(captureRequest())).isEqualTo(CaptureResult.AUTHORIZATION_EXPIRED);
        }

        @Test
        @DisplayName("Stripe の 5xx は、接続できない")
        void serverError() {
            // 準備：Stripe の側の故障
            respond("POST /v1/payment_intents/" + INTENT_ID + "/capture", 500, StripeApiStub.error("api_error", null));

            // 操作と確認：接続できない
            assertThat(gateway.capture(captureRequest())).isEqualTo(CaptureResult.UNREACHABLE);
        }

        @Test
        @DisplayName("Stripe に接続できなければ、接続できない")
        void unreachable() throws IOException {
            // 準備：閉じたポートにつなぐ実装
            var unreachable = unreachableGateway();

            // 操作と確認：接続できない
            assertThat(unreachable.capture(captureRequest())).isEqualTo(CaptureResult.UNREACHABLE);
        }

        @Test
        @DisplayName("売上確定の回数の制限は、接続できないと同じく、接続できないという結果を返す")
        void rateLimitIsUnreachable() {
            // 準備：回数の制限
            respond("POST /v1/payment_intents/" + INTENT_ID + "/capture", 429,
                    StripeApiStub.error("invalid_request_error", "rate_limit"));

            // 操作と確認：接続できない
            assertThat(gateway.capture(captureRequest())).isEqualTo(CaptureResult.UNREACHABLE);
        }

        @Test
        @DisplayName("他の拒否は、予期しないエラー")
        void otherRejection() {
            // 準備：状態によらない拒否
            respond("POST /v1/payment_intents/" + INTENT_ID + "/capture", 400,
                    StripeApiStub.error("invalid_request_error", "amount_too_large"));

            // 操作と確認：予期しないエラー
            assertSystemError(() -> gateway.capture(captureRequest()), CommonErrorCode.SYSTEM_ERROR);
        }
    }

    @Nested
    @DisplayName("返金")
    class Refund {

        @Test
        @DisplayName("PaymentIntent の総額を返金し、冪等キーを付ける")
        void refunds() {
            // 準備：返金に成功する
            respond("POST /v1/refunds", 200, REFUND_SUCCEEDED);

            // 操作：返金する
            gateway.refund(refundRequest());

            // 確認：PaymentIntent・総額と、冪等キーが付いていること
            var request = requests().getFirst();
            assertThat(request.body()).contains("payment_intent=" + INTENT_ID, "amount=" + AMOUNT_YEN);
            assertThat(request.idempotencyKey()).isNotBlank();
        }

        @Test
        @DisplayName("AC-616-2 5xx で失敗した後の再実行は、別の冪等キーで返金し、成功する")
        void retriesWithDifferentKeyAfterServerError() {
            // 準備：1 回目は Stripe の側の故障（外部サービスのエラー）
            respond("POST /v1/refunds", 500, StripeApiStub.error("api_error", null));
            assertSystemError(() -> gateway.refund(refundRequest()), PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);

            // 準備：2 回目までに Stripe が回復する
            respond("POST /v1/refunds", 200, REFUND_SUCCEEDED);

            // 操作：返金する（例外にならない）
            gateway.refund(refundRequest());

            // 確認：冪等キーが 1 回目と異なる
            assertDifferentIdempotencyKeys();
        }

        @Test
        @DisplayName("NFR-616-2 stripe-java の自動の再試行で 5xx の後に成功したら、同じ冪等キーで 2 回送り、成功する")
        void automaticRetrySendsSameKey() {
            // 準備：自動の再試行を 1 回にし、1 回目は 5xx、2 回目は成功
            var retrying = new StripePaymentGateway(stripe.clientWithRetries(1), Clock.fixed(NOW, ZoneOffset.UTC));
            stripe.respondOnce("POST /v1/refunds", 500, StripeApiStub.error("api_error", null));
            respond("POST /v1/refunds", 200, REFUND_SUCCEEDED);

            // 操作：返金する（例外にならない）
            retrying.refund(refundRequest());

            // 確認：1 回の呼び出しで送られた 2 つの POST が同じ冪等キー
            assertSameIdempotencyKeyInOneCall();
        }

        @Test
        @DisplayName("AC-616-4 1 回目が完了していて応答だけ 5xx のとき、再実行で断られても（charge_already_refunded）、別の冪等キーで成功する")
        void treatsAlreadyRefundedAfterServerErrorAsSuccess() {
            // 準備：1 回目は 5xx
            respond("POST /v1/refunds", 500, StripeApiStub.error("api_error", null));
            assertSystemError(() -> gateway.refund(refundRequest()), PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);

            // 準備：2 回目は、返金済みで断られる
            respond("POST /v1/refunds", 400, StripeApiStub.error("invalid_request_error", "charge_already_refunded"));

            // 操作：返金する（例外にならない）
            gateway.refund(refundRequest());

            // 確認：冪等キーが 1 回目と異なる
            assertDifferentIdempotencyKeys();
        }

        @Test
        @DisplayName("返金済みで断られたら（charge_already_refunded）、成功とする")
        void alreadyRefunded() {
            // 準備：返金済みで断られる
            respond("POST /v1/refunds", 400, StripeApiStub.error("invalid_request_error", "charge_already_refunded"));

            // 操作：返金する（例外にならない）
            gateway.refund(refundRequest());

            // 確認：やり直さない
            assertThat(requests()).hasSize(1);
        }

        @Test
        @DisplayName("返金済み以外の理由の 400 は、成功とせず、予期しないエラーにする")
        void otherInvalidRequest() {
            // 準備：返金済みでない理由で断られる
            respond("POST /v1/refunds", 400, StripeApiStub.error("invalid_request_error", "amount_too_large"));

            // 操作と確認：予期しないエラー
            assertSystemError(() -> gateway.refund(refundRequest()), CommonErrorCode.SYSTEM_ERROR);
        }

        @Test
        @DisplayName("他の失敗は、外部サービスのエラー（再発行でやり直す）")
        void failure() {
            // 準備：Stripe の側の故障
            respond("POST /v1/refunds", 500, StripeApiStub.error("api_error", null));

            // 操作と確認：外部サービスのエラー
            assertSystemError(() -> gateway.refund(refundRequest()), PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }
    }

    @Nested
    @DisplayName("支払画面の作成")
    class OpenPage {

        @Test
        @DisplayName("作った Checkout Session の ID と URL を、支払画面として返す")
        void returnsPage() {
            // 準備：作成に成功する
            respond("POST /v1/checkout/sessions", 200, session("open"));

            // 操作：支払画面を作る
            var page = gateway.openPage(pageRequest());

            // 確認：画面の ID と URL
            assertThat(page).isEqualTo(new PaymentPage(SESSION_ID, SESSION_URL));
        }

        @Test
        @DisplayName("与信だけを取る Checkout Session を、総額・戻り先・31 分の期限で作る")
        void opensAuthorizationOnlySession() {
            // 準備：作成に成功する
            respond("POST /v1/checkout/sessions", 200, session("open"));

            // 操作：支払画面を作る
            gateway.openPage(pageRequest());

            // 確認：与信だけ、総額、戻り先、注文と支払の ID、期限
            assertAuthorizationOnlySession(requests().getFirst().body());
        }

        @Test
        @DisplayName("AC-518-8 上限ちょうどの金額でも、上限の金額を 1 件の明細で渡す")
        void passesUpperLimitAsSingleLineItem() {
            // 準備：作成に成功する。金額は支払 1 回の金額の上限ちょうど
            respond("POST /v1/checkout/sessions", 200, session("open"));
            var limit = PaymentAmountRange.STANDARD.max();

            // 操作：上限の金額で支払画面を作る
            gateway.openPage(new PageRequest(PAYMENT_ID, ORDER_ID, limit, RETURN_URL));

            // 確認：上限の金額の明細が 1 件だけ（2 件目の明細がない）
            var body = requests().getFirst().body();
            assertThat(body).contains("line_items[0][price_data][unit_amount]=" + limit.yen(),
                    "line_items[0][quantity]=1");
            assertThat(body).doesNotContain("line_items[1]");
        }

        @Test
        @DisplayName("支払画面の期限は、支払の期限の上限（deadline の @DurationMax）より長い")
        void pageLifetimeExceedsDeadlineLimit() throws NoSuchFieldException {
            // 準備：作成に成功する
            respond("POST /v1/checkout/sessions", 200, session("open"));

            // 操作：支払画面を作る
            gateway.openPage(pageRequest());

            // 確認：Stripe に渡した期限が、設定の注釈の上限より長い
            // 期限の定数は private で、テストのために広げない。Stripe に渡した expires_at から期限を求める。
            // 上限はテストに値を写さず、設定の注釈から読む（上限を延ばしたときに、このテストが失敗して気づく）
            var matcher = Pattern.compile("expires_at=(\\d+)").matcher(requests().getFirst().body());
            assertThat(matcher.find()).isTrue();
            var pageLifetime = Duration.between(NOW, Instant.ofEpochSecond(Long.parseLong(matcher.group(1))));
            var limit = PaymentProperties.class.getDeclaredField("deadline").getAnnotation(DurationMax.class);
            var deadlineLimit = Duration.ofDays(limit.days()).plusHours(limit.hours()).plusMinutes(limit.minutes())
                    .plusSeconds(limit.seconds()).plusMillis(limit.millis()).plusNanos(limit.nanos());
            assertThat(pageLifetime).isGreaterThan(deadlineLimit);
        }

        @Test
        @DisplayName("作れなければ、外部サービスのエラー")
        void failure() {
            // 準備：Stripe の側の故障
            respond("POST /v1/checkout/sessions", 500, StripeApiStub.error("api_error", null));

            // 操作と確認：外部サービスのエラー
            assertSystemError(() -> gateway.openPage(pageRequest()), PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }
    }

    @Nested
    @DisplayName("支払画面を閉じる")
    class ClosePage {

        @Test
        @DisplayName("開いている Session を期限切れにし、閉じた")
        void closes() {
            // 準備：期限切れにできる
            respond("POST /v1/checkout/sessions/" + SESSION_ID + "/expire", 200, session("expired"));

            // 操作と確認：閉じた
            assertThat(gateway.closePage(SESSION_ID)).isEqualTo(CloseResult.CLOSED);
        }

        @Test
        @DisplayName("閉じられず、読み直すと支払が済んでいたら（complete）、支払済み")
        void alreadyPaid() {
            // 準備：閉じられず、読み直すと支払済み
            respond("POST /v1/checkout/sessions/" + SESSION_ID + "/expire", 400,
                    StripeApiStub.error("invalid_request_error", "checkout_session_not_open"));
            respond("GET /v1/checkout/sessions/" + SESSION_ID, 200, session("complete"));

            // 操作と確認：支払済み
            assertThat(gateway.closePage(SESSION_ID)).isEqualTo(CloseResult.ALREADY_PAID);
        }

        @Test
        @DisplayName("閉じられず、読み直すと閉じていたら（expired）、閉じた")
        void alreadyClosed() {
            // 準備：閉じられず、読み直すと閉じている
            respond("POST /v1/checkout/sessions/" + SESSION_ID + "/expire", 400,
                    StripeApiStub.error("invalid_request_error", "checkout_session_not_open"));
            respond("GET /v1/checkout/sessions/" + SESSION_ID, 200, session("expired"));

            // 操作と確認：閉じた
            assertThat(gateway.closePage(SESSION_ID)).isEqualTo(CloseResult.CLOSED);
        }

        @Test
        @DisplayName("Stripe の 5xx は、外部サービスのエラー")
        void failure() {
            // 準備：Stripe の側の故障
            respond("POST /v1/checkout/sessions/" + SESSION_ID + "/expire", 500, StripeApiStub.error("api_error", null));

            // 操作と確認：外部サービスのエラー
            assertSystemError(() -> gateway.closePage(SESSION_ID), PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }
    }

    @Nested
    @DisplayName("与信の取得")
    class FetchAuthorization {

        /**
         * PaymentIntent の作成時刻（エポック秒）
         */
        private static final long INTENT_CREATED = 1_793_926_800L;

        @Test
        @DisplayName("AC-409-2 PaymentIntent を含めて取得した Session から、与信の参照（PaymentIntent の ID）と与信の時刻（作成時刻）を読む")
        void fetches() {
            // 準備：支払の済んだ Session
            respond("GET /v1/checkout/sessions/" + SESSION_ID, 200,
                    StripeApiStub.completedSession(SESSION_ID, SESSION_URL, INTENT_ID, "requires_capture", INTENT_CREATED));

            // 操作：与信を取得する
            var authorization = gateway.fetchAuthorization(SESSION_ID);

            // 確認：PaymentIntent の ID と作成時刻
            assertThat(authorization).isEqualTo(new Authorization(INTENT_ID, Instant.ofEpochSecond(INTENT_CREATED)));
        }

        @Test
        @DisplayName("Session の取得に、PaymentIntent を含める指定（expand）を付ける")
        void expandsPaymentIntent() {
            // 準備：支払の済んだ Session
            respond("GET /v1/checkout/sessions/" + SESSION_ID, 200,
                    StripeApiStub.completedSession(SESSION_ID, SESSION_URL, INTENT_ID, "requires_capture", INTENT_CREATED));

            // 操作：与信を取得する
            gateway.fetchAuthorization(SESSION_ID);

            // 確認：Session の取得 1 回に、expand を付けた
            assertThat(requests()).singleElement().satisfies(request -> {
                assertThat(request.key()).isEqualTo("GET /v1/checkout/sessions/" + SESSION_ID);
                assertThat(request.query()).contains("expand[0]=payment_intent");
            });
        }

        @Test
        @DisplayName("Session が complete でなければ（expired）、予期しないエラー")
        void rejectsIncompleteSession() {
            // 準備：期限切れの Session
            respond("GET /v1/checkout/sessions/" + SESSION_ID, 200, session("expired"));

            // 操作と確認：予期しないエラー
            assertSystemError(() -> gateway.fetchAuthorization(SESSION_ID), CommonErrorCode.SYSTEM_ERROR);
        }

        @Test
        @DisplayName("complete でも PaymentIntent がなければ、予期しないエラー")
        void rejectsSessionWithoutIntent() {
            // 準備：PaymentIntent のない、支払の済んだ Session
            respond("GET /v1/checkout/sessions/" + SESSION_ID, 200, session("complete"));

            // 操作と確認：予期しないエラー
            assertSystemError(() -> gateway.fetchAuthorization(SESSION_ID), CommonErrorCode.SYSTEM_ERROR);
        }

        @Test
        @DisplayName("Stripe の 5xx は、外部サービスのエラー")
        void serverError() {
            // 準備：Stripe の側の故障
            respond("GET /v1/checkout/sessions/" + SESSION_ID, 500, StripeApiStub.error("api_error", null));

            // 操作と確認：外部サービスのエラー
            assertSystemError(() -> gateway.fetchAuthorization(SESSION_ID), PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }
    }

    @Nested
    @DisplayName("与信の取消")
    class VoidAuthorization {

        @Test
        @DisplayName("PaymentIntent を取り消す")
        void voids() {
            // 準備：取消に成功する
            respond("POST /v1/payment_intents/" + INTENT_ID + "/cancel", 200, intent("canceled"));

            // 操作：取り消す
            var result = gateway.voidAuthorization(INTENT_ID);

            // 確認：取り消した結果で、取消のリクエストだけを送った
            assertThat(result).isEqualTo(VoidResult.VOIDED);
            assertThat(requests()).extracting(Request::key).containsExactly("POST /v1/payment_intents/" + INTENT_ID + "/cancel");
        }

        @Test
        @DisplayName("取消済みで断られたら（状態 canceled）、成功とする")
        void alreadyVoided() {
            // 準備：取消は断られ、読み直すと取消済み
            respond("POST /v1/payment_intents/" + INTENT_ID + "/cancel", 400,
                    StripeApiStub.error("invalid_request_error", "payment_intent_unexpected_state"));
            respond("GET /v1/payment_intents/" + INTENT_ID, 200, intent("canceled"));

            // 操作と確認：取り消した
            assertThat(gateway.voidAuthorization(INTENT_ID)).isEqualTo(VoidResult.VOIDED);
        }

        @Test
        @DisplayName("売上確定済みで断られたら（状態 succeeded）、例外にせず、売上確定済みを返す")
        void alreadyCaptured() {
            // 準備：取消は断られ、読み直すと売上確定済み
            respond("POST /v1/payment_intents/" + INTENT_ID + "/cancel", 400,
                    StripeApiStub.error("invalid_request_error", "payment_intent_unexpected_state"));
            respond("GET /v1/payment_intents/" + INTENT_ID, 200, intent("succeeded"));

            // 操作と確認：売上確定済み
            assertThat(gateway.voidAuthorization(INTENT_ID)).isEqualTo(VoidResult.ALREADY_CAPTURED);
        }

        @Test
        @DisplayName("AC-407-4 断られ、取消済みでも売上確定済みでもなければ（状態 requires_capture）、システムのエラー")
        void rejected() {
            // 準備：取消は断られ、読み直すと与信済みのまま
            respond("POST /v1/payment_intents/" + INTENT_ID + "/cancel", 400,
                    StripeApiStub.error("invalid_request_error", "payment_intent_unexpected_state"));
            respond("GET /v1/payment_intents/" + INTENT_ID, 200, intent("requires_capture"));

            // 操作と確認：予期しないエラー（再発行でやり直す）
            assertSystemError(() -> gateway.voidAuthorization(INTENT_ID), CommonErrorCode.SYSTEM_ERROR);
        }

        @Test
        @DisplayName("Stripe の 5xx は、外部サービスのエラー（再発行でやり直す）")
        void failure() {
            // 準備：Stripe の側の故障
            respond("POST /v1/payment_intents/" + INTENT_ID + "/cancel", 500, StripeApiStub.error("api_error", null));

            // 操作と確認：外部サービスのエラー
            assertSystemError(() -> gateway.voidAuthorization(INTENT_ID), PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }
    }

    @Nested
    @DisplayName("売上確定の状態の取得")
    class FetchCaptureState {

        @ParameterizedTest(name = "PaymentIntent の状態 {0} は {1}")
        @CsvSource({
                "succeeded, CAPTURED",
                "requires_capture, NOT_CAPTURED",
                "canceled, AUTHORIZATION_EXPIRED"})
        @DisplayName("AC-423-1、AC-423-5、AC-423-6 PaymentIntent の状態を、売上確定の状態に対応させる")
        void mapsIntentStatus(String intentStatus, CaptureState expected) {
            // 準備：指定の状態の PaymentIntent
            respond("GET /v1/payment_intents/" + INTENT_ID, 200, intent(intentStatus));

            // 操作と確認：対応する売上確定の状態
            assertThat(gateway.fetchCaptureState(INTENT_ID)).isEqualTo(expected);
        }

        @Test
        @DisplayName("PaymentIntent を読むだけで、売上確定も取消も求めない")
        void onlyReads() {
            // 準備：与信だけの PaymentIntent
            respond("GET /v1/payment_intents/" + INTENT_ID, 200, intent("requires_capture"));

            // 操作：状態を取得する
            gateway.fetchCaptureState(INTENT_ID);

            // 確認：読むリクエストだけを送った
            assertThat(requests()).extracting(Request::key).containsExactly("GET /v1/payment_intents/" + INTENT_ID);
        }

        @ParameterizedTest(name = "PaymentIntent の状態 {0}")
        @ValueSource(strings = {"requires_payment_method", "processing"})
        @DisplayName("それ以外の状態は、予期しないエラー")
        void rejectsOtherStatus(String intentStatus) {
            // 準備：与信を取った後には現れない状態の PaymentIntent
            respond("GET /v1/payment_intents/" + INTENT_ID, 200, intent(intentStatus));

            // 操作と確認：予期しないエラー
            assertSystemError(() -> gateway.fetchCaptureState(INTENT_ID), CommonErrorCode.SYSTEM_ERROR);
        }

        @Test
        @DisplayName("Stripe の 5xx は、外部サービスのエラー")
        void failure() {
            // 準備：Stripe の側の故障
            respond("GET /v1/payment_intents/" + INTENT_ID, 500, StripeApiStub.error("api_error", null));

            // 操作と確認：外部サービスのエラー
            assertSystemError(() -> gateway.fetchCaptureState(INTENT_ID), PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }
    }

    @Nested
    @DisplayName("失敗の分類")
    class FailureClassification {

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("com.ecsite.module.payment.infrastructure.stripe.StripePaymentGatewayTest#operations")
        @DisplayName("AC-294 Stripe の側の故障（5xx）は、外部サービスのエラー")
        void serverErrorIsUnavailable(String name, String key, Consumer<StripePaymentGateway> operation) {
            // 準備：Stripe の側の故障
            respond(key, 500, StripeApiStub.error("api_error", null));

            // 操作と確認：外部サービスのエラー
            assertSystemError(() -> operation.accept(gateway), PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("com.ecsite.module.payment.infrastructure.stripe.StripePaymentGatewayTest#operations")
        @DisplayName("AC-294 回数の制限（429）は、外部サービスのエラー")
        void rateLimitIsUnavailable(String name, String key, Consumer<StripePaymentGateway> operation) {
            // 準備：回数の制限
            respond(key, 429, StripeApiStub.error("invalid_request_error", "rate_limit"));

            // 操作と確認：外部サービスのエラー
            assertSystemError(() -> operation.accept(gateway), PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("com.ecsite.module.payment.infrastructure.stripe.StripePaymentGatewayTest#operations")
        @DisplayName("AC-294 認証の誤り（401）は、予期しないエラー（設定の誤り）")
        void authenticationErrorIsSystemError(String name, String key, Consumer<StripePaymentGateway> operation) {
            // 準備：認証の誤り
            respond(key, 401, StripeApiStub.error("invalid_request_error", null));

            // 操作と確認：予期しないエラー
            assertSystemError(() -> operation.accept(gateway), CommonErrorCode.SYSTEM_ERROR);
        }

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("com.ecsite.module.payment.infrastructure.stripe.StripePaymentGatewayTest#operations")
        @DisplayName("AC-295 Stripe に接続できなければ、外部サービスのエラー")
        void unreachableIsUnavailable(String name, String key, Consumer<StripePaymentGateway> operation) throws IOException {
            // 準備：閉じたポートにつなぐ実装
            var unreachable = unreachableGateway();

            // 操作と確認：外部サービスのエラー
            assertSystemError(() -> operation.accept(unreachable), PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }

        @Test
        @DisplayName("AC-294 状態の読み取り（閉じられなかった画面の読み直し）で Stripe の側が故障していれば、外部サービスのエラー")
        void retrieveServerErrorIsUnavailable() {
            // 準備：閉じられず、読み直しで Stripe の側の故障
            respond("POST /v1/checkout/sessions/" + SESSION_ID + "/expire", 400,
                    StripeApiStub.error("invalid_request_error", "checkout_session_not_open"));
            respond("GET /v1/checkout/sessions/" + SESSION_ID, 503, StripeApiStub.error("api_error", null));

            // 操作と確認：外部サービスのエラー
            assertSystemError(() -> gateway.closePage(SESSION_ID), PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }
    }

    /**
     * 失敗の分類を確認する操作（名前、Stripe のリクエスト、呼び出し）。
     *
     * <p>売上確定は結果で返すので除く。
     *
     * @return 操作の一覧
     */
    static Stream<Arguments> operations() {
        return Stream.of(
                Arguments.of("支払画面を作る", "POST /v1/checkout/sessions",
                        (Consumer<StripePaymentGateway>) gateway -> gateway.openPage(pageRequest())),
                Arguments.of("支払画面を閉じる", "POST /v1/checkout/sessions/" + SESSION_ID + "/expire",
                        (Consumer<StripePaymentGateway>) gateway -> gateway.closePage(SESSION_ID)),
                Arguments.of("返金", "POST /v1/refunds",
                        (Consumer<StripePaymentGateway>) gateway -> gateway.refund(refundRequest())),
                Arguments.of("与信の取消", "POST /v1/payment_intents/" + INTENT_ID + "/cancel",
                        (Consumer<StripePaymentGateway>) gateway -> gateway.voidAuthorization(INTENT_ID)),
                Arguments.of("与信の取得", "GET /v1/checkout/sessions/" + SESSION_ID,
                        (Consumer<StripePaymentGateway>) gateway -> gateway.fetchAuthorization(SESSION_ID)),
                Arguments.of("売上確定の状態の取得", "GET /v1/payment_intents/" + INTENT_ID,
                        (Consumer<StripePaymentGateway>) gateway -> gateway.fetchCaptureState(INTENT_ID)));
    }

    // ============================== 確認の補助 ==============================

    /**
     * 支払画面の作成のリクエストが、与信だけを取る Checkout Session の作成であることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>支払のモード</li>
     *   <li>売上を手動で確定する（与信だけを取る）</li>
     *   <li>明細 1 行の単価が総額（{@link #AMOUNT_YEN}）、通貨が円、数量が 1</li>
     *   <li>成功とやめたときの戻り先が {@link #RETURN_URL}</li>
     *   <li>注文 ID（{@code client_reference_id}）と支払 ID（Session の metadata）</li>
     *   <li>PaymentIntent の metadata の注文 ID</li>
     *   <li>期限が現在時刻の 31 分後</li>
     * </ul>
     *
     * @param body 作成のリクエストの本文
     */
    private static void assertAuthorizationOnlySession(String body) {
        assertThat(body).contains("mode=payment");
        assertThat(body).contains("payment_intent_data[capture_method]=manual");
        assertThat(body).contains("line_items[0][price_data][unit_amount]=" + AMOUNT_YEN,
                "line_items[0][price_data][currency]=jpy", "line_items[0][quantity]=1");
        assertThat(body).contains("success_url=" + RETURN_URL, "cancel_url=" + RETURN_URL);
        assertThat(body).contains("client_reference_id=" + ORDER_ID, "metadata[paymentId]=" + PAYMENT_ID);
        assertThat(body).contains("payment_intent_data[metadata][orderId]=" + ORDER_ID);
        assertThat(body).contains("expires_at=" + NOW.plus(PAGE_LIFETIME).getEpochSecond());
    }

    // ============================== 準備の補助 ==============================

    /**
     * 閉じたポートにつなぐ、Stripe に接続できない実装を作る。
     *
     * @return 実装
     * @throws IOException 閉じたポートを用意できないとき
     */
    private static StripePaymentGateway unreachableGateway() throws IOException {
        return new StripePaymentGateway(StripeApiStub.client("http://localhost:" + StripeApiStub.closedPort()),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /**
     * 支払画面の作成のリクエストを作る。
     *
     * @return リクエスト
     */
    private static PageRequest pageRequest() {
        return new PageRequest(PAYMENT_ID, ORDER_ID, Money.of(AMOUNT_YEN), RETURN_URL);
    }

    /**
     * 指定の状態の Checkout Session の応答を作る。
     *
     * @param status Session の状態
     * @return 応答の本文
     */
    private static String session(String status) {
        return StripeApiStub.session(SESSION_ID, SESSION_URL, status);
    }

    /**
     * 売上確定のリクエストを作る。
     *
     * @return リクエスト
     */
    private static CaptureRequest captureRequest() {
        return new CaptureRequest(ORDER_ID, INTENT_ID, Money.of(AMOUNT_YEN));
    }

    /**
     * 返金のリクエストを作る。
     *
     * @return リクエスト
     */
    private static RefundRequest refundRequest() {
        return new RefundRequest(ORDER_ID, INTENT_ID, Money.of(AMOUNT_YEN));
    }

    /**
     * Stripe の API の応答を決める。
     *
     * @param key    リクエストのメソッドとパス
     * @param status HTTP ステータス
     * @param body   応答の本文
     */
    private void respond(String key, int status, String body) {
        stripe.respond(key, status, body);
    }

    /**
     * Stripe が受けたリクエストを返す。
     *
     * @return 受けた順のリクエスト
     */
    private List<Request> requests() {
        return stripe.requests();
    }

    /**
     * 指定の状態の PaymentIntent の応答を作る。
     *
     * @param status PaymentIntent の状態
     * @return 応答の本文
     */
    private static String intent(String status) {
        return StripeApiStub.intent(INTENT_ID, status);
    }

    /**
     * 1 回の呼び出しで、自動の再試行によって送られた 2 つの POST が、同じで空でない冪等キーを付けたことを確認する。
     */
    private void assertSameIdempotencyKeyInOneCall() {
        var keys = requests().stream()
                .filter(request -> request.key().startsWith("POST "))
                .map(Request::idempotencyKey)
                .toList();
        assertThat(keys).hasSize(2).doesNotContainNull();
        assertThat(keys.getFirst()).isNotBlank();
        assertThat(keys.get(1)).isEqualTo(keys.getFirst());
    }

    /**
     * 売上確定または返金の 2 回の呼び出しが、別の冪等キーを付けたことを確認する。
     *
     * <p>自動の再試行は切ってある（{@link StripeApiStub#client(String)}）。
     * そのため、売上確定と返金のリクエストの 1 つ目が 1 回目、最後が 2 回目の呼び出しになる。
     * PaymentIntent の再取得（GET）は、冪等キーを付けないので数えない。
     */
    private void assertDifferentIdempotencyKeys() {
        var keys = requests().stream()
                .filter(request -> request.key().startsWith("POST "))
                .map(Request::idempotencyKey)
                .toList();
        assertThat(keys).hasSize(2).doesNotContainNull().doesNotHaveDuplicates();
    }
}
