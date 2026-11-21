package com.ecsite.module.order.domain.order;

import com.ecsite.common.model.value.Money;
import com.ecsite.common.model.value.Operator;
import com.ecsite.module.order.domain.OrderErrorCode;
import com.ecsite.module.order.domain.order.vo.BuyerProfile;
import com.ecsite.module.order.domain.order.vo.Cancellation;
import com.ecsite.module.order.domain.order.vo.CancellationKind;
import com.ecsite.module.order.domain.order.vo.CancellationRequest;
import com.ecsite.module.order.domain.order.vo.OrderLine;
import com.ecsite.module.order.domain.order.vo.OrderNumber;
import com.ecsite.module.order.domain.order.vo.ShippingAddress;
import com.ecsite.module.order.domain.quote.Amounts;
import com.ecsite.module.order.domain.quote.AxisValue;
import com.ecsite.module.order.domain.quote.Quote;
import com.ecsite.module.order.domain.quote.QuoteItem;
import com.ecsite.module.order.domain.quote.TaxAmount;
import com.ecsite.module.order.domain.quote.VariantInfo;
import com.ecsite.module.order.event.OrderStatusChanged;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 注文の単体テスト
 *
 * <p>注文の成立（{@link Order#place}）と、復元（{@link Order#restore}）のときのメールアドレスと、状態と取消の情報の整合と、遷移のメソッドで作った注文がそのまま復元できることを確認する。
 * 見積もりが注文できるかの判定（{@code orderable}）は
 * {@code QuoteTest}、API を通した流れは {@code CheckoutStartApiTest}・{@code PlaceOrderPlacementApiTest} などが確認する。
 *
 * <p>準備のデータと確認の期待値は、同じ定数から作る（準備した物から期待値を取り出さない）。
 */
class OrderTest {

    // ============================== 定数 ==============================

    /**
     * 注文日時
     *
     * <p>日本時間の 2026-10-08 00:30。
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * お届け先
     */
    private static final ShippingAddress ADDRESS =
            ShippingAddress.of("山田 太郎", "100-0001", TestAreas.CHIYODA, "千代田 1-1", "03-1234-5678");

    /**
     * 注文のメールアドレス
     */
    private static final String EMAIL = "taro@example.com";

    /**
     * T シャツの品番
     */
    private static final String PRODUCT_CODE = "TS-001";

    /**
     * T シャツ（白・M）のバリアント品番
     */
    private static final String VARIANT_CODE = "TS-001-WM";

    /**
     * T シャツの商品名
     */
    private static final String PRODUCT_NAME = "T シャツ";

    /**
     * T シャツ（白・M）の軸の値
     */
    private static final List<AxisValue> AXIS_VALUES = List.of(new AxisValue("色", "白"), new AxisValue("サイズ", "M"));

    /**
     * T シャツの税抜単価
     */
    private static final Money UNIT_PRICE_EXCL_TAX = Money.of(2700);

    /**
     * T シャツの税率（%）
     */
    private static final BigDecimal RATE_PERCENT = BigDecimal.TEN;

    /**
     * T シャツの税込単価
     */
    private static final Money UNIT_PRICE_INCL_TAX = Money.of(2970);

    /**
     * 注文できる見積もりの数量
     */
    private static final int QUANTITY = 2;

    /**
     * 注文できる見積もりの小計（税込単価 × 数量）
     */
    private static final Money SUBTOTAL = Money.of(5940);

    // ============================== テスト ==============================

    @Nested
    @DisplayName("注文の成立")
    class Placement {

        @Test
        @DisplayName("AC-143、BR-14 成立した注文は「支払待ち」になる")
        void isPendingPayment() {
            // 操作：注文できる見積もりから注文を作る
            var order = place(quote());

            // 確認：状態が「支払待ち」
            assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        }

        @Test
        @DisplayName("AC-143、BR-14 成立すると、注文成立のイベントが 1 件だけ登録される")
        void registersPlacedEvent() {
            // 操作：注文できる見積もりから注文を作る
            var order = place(quote());

            // 確認：この注文の「なし → 支払待ち」のイベントだけが登録されている
            assertPlacedEventOnly(order);
        }

        @Test
        @DisplayName("DC-484-1 成立した注文は、見積もりの送料の消費税を持つ")
        void keepsShippingTaxOfQuote() {
            // 操作：送料の消費税が 90 円の見積もりから注文を作る
            var order = place(quote());

            // 確認：送料の消費税が 90 円
            assertThat(order.shippingTax()).isEqualTo(Money.of(90));
        }

        @Test
        @DisplayName("DC-484-3 送料の消費税が 0 の見積もりから作った注文は、送料の消費税を 0 で持つ（null でない）")
        void keepsZeroShippingTax() {
            // 準備：送料の消費税が 0 円の見積もり
            var item = QuoteItem.of(tShirt(), QUANTITY, 10, RATE_PERCENT, UNIT_PRICE_INCL_TAX);
            var amounts = new Amounts(SUBTOTAL, Money.ZERO, SUBTOTAL, List.of(new TaxAmount(RATE_PERCENT, SUBTOTAL, Money.of(540))));
            var freeShipping = new Quote(List.of(item), amounts, Money.ZERO, true);

            // 操作：注文を作る
            var order = place(freeShipping);

            // 確認：送料の消費税が 0 円
            assertThat(order.shippingTax()).isEqualTo(Money.ZERO);
        }
    }

    @Nested
    @DisplayName("明細と注文の項目")
    class Lines {

        @Test
        @DisplayName("AC-92 明細の数は、見積もりの品目の数と同じ")
        void hasOneLinePerItem() {
            // 操作：品目が 1 つの見積もりから注文を作る
            var order = place(quote());

            // 確認：明細が 1 行
            assertThat(order.lines()).hasSize(1);
        }

        @Test
        @DisplayName("明細の行番号は、1 から見積もりの品目の並びで振る")
        void numbersLinesInQuoteOrder() {
            // 準備：品番 A・B・C の順に並ぶ 3 品目の見積もり
            var quote = quoteOf(List.of("TS-001-A", "TS-001-B", "TS-001-C"));

            // 操作：注文を作る
            var order = place(quote);

            // 確認：行番号が 1・2・3 で、並びが見積もりのまま
            assertThat(order.lines()).extracting(OrderLine::lineNo, OrderLine::variantCode)
                    .containsExactly(tuple(1, "TS-001-A"), tuple(2, "TS-001-B"), tuple(3, "TS-001-C"));
        }

        @Test
        @DisplayName("AC-92 明細は、注文時点の品番・商品名・軸の値・税抜単価・税率・税込単価の写しと、数量・小計を持つ")
        void copiesItemIntoLine() {
            // 準備：T シャツ 2 枚の見積もり
            var quote = quote();

            // 操作：注文を作る
            var order = place(quote);

            // 確認：明細の全ての項目が、見積もりの品目の写し
            assertLineCopiedFromQuote(order.lines().getFirst(), quote.items().getFirst().variant().variantId());
        }

        @Test
        @DisplayName("AC-92 注文は、見積もりの金額の内訳を持つ")
        void keepsAmounts() {
            // 準備：T シャツ 2 枚の見積もり
            var quote = quote();

            // 操作：注文を作る
            var order = place(quote);

            // 確認：金額の内訳が見積もりと同じ
            assertThat(order.amounts()).isEqualTo(quote.amounts());
        }

        @Test
        @DisplayName("AC-92 注文は、入力したメールアドレスとお届け先を持つ")
        void keepsBuyerInput() {
            // 操作：メールアドレスとお届け先を渡して注文を作る
            var order = place(quote());

            // 確認：渡した値のまま
            assertThat(order.email()).isEqualTo(EMAIL);
            assertThat(order.shippingAddress()).isEqualTo(ADDRESS);
        }
    }

    @Nested
    @DisplayName("注文できない見積もり")
    class UnorderableQuote {

        @Test
        @DisplayName("AC-88 空の見積もりからは注文を作れない")
        void rejectsEmptyQuote() {
            // 準備：品目のない見積もり
            var empty = new Quote(List.of(), new Amounts(Money.ZERO, Money.ZERO, Money.ZERO, List.of()), Money.ZERO, false);

            // 操作と確認：プログラムの誤りとして拒否される
            assertThatThrownBy(() -> place(empty)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("AC-88 購入できない品目を含む見積もりからは注文を作れない")
        void rejectsQuoteWithUnpurchasableItem() {
            // 準備：在庫が 0 で購入できない品目を含む見積もり
            var unorderable = quoteWithOutOfStockItem();

            // 確認（前提）：品目が購入できず、見積もりが注文できない
            assertThat(unorderable.items().getFirst().purchasable()).isFalse();
            assertThat(unorderable.orderable()).isFalse();

            // 操作と確認：プログラムの誤りとして拒否される
            assertThatThrownBy(() -> place(unorderable)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("メールアドレスの検証")
    class EmailValidation {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"", " ", "taro", "taro@", "@example.com"})
        @DisplayName("AC-86 メールアドレスの形が違えば拒否される")
        void rejectsInvalidEmail(String email) {
            // 操作と確認：業務のエラー INVALID_EMAIL で拒否される
            assertBusinessError(() -> Order.place(UUID.randomUUID(), OrderNumber.of("20261008-000001"), UUID.randomUUID(), UUID.randomUUID(),
                    ORDERED_AT, quote(), email, ADDRESS, BuyerProfile.UNKNOWN), OrderErrorCode.INVALID_EMAIL);
        }

        @Test
        @DisplayName("メールアドレスがなければ拒否される")
        void rejectsMissingEmail() {
            // 操作と確認：業務のエラー INVALID_EMAIL で拒否される
            assertBusinessError(() -> placeWithEmail(null), OrderErrorCode.INVALID_EMAIL);
        }

        @Test
        @DisplayName("上限の 254 文字ちょうどのメールアドレスで注文が成立する")
        void acceptsEmailAtMaxLength() {
            // 準備：形が正しく、254 文字のメールアドレス
            var email = emailOfLength(254);

            // 操作：注文を作る
            var order = placeWithEmail(email);

            // 確認：渡したメールアドレスのまま
            assertThat(order.email()).isEqualTo(email);
        }

        @Test
        @DisplayName("上限を 1 文字超える 255 文字のメールアドレスは拒否される")
        void rejectsEmailOverMaxLength() {
            // 準備：形が正しく、255 文字のメールアドレス
            var email = emailOfLength(255);

            // 操作と確認：形が正しくても、業務のエラー INVALID_EMAIL で拒否される
            assertBusinessError(() -> placeWithEmail(email), OrderErrorCode.INVALID_EMAIL);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"", "taro", "taro@", "@example.com"})
        @DisplayName("形の違うメールアドレスの注文は、復元でも拒否される")
        void rejectsInvalidEmailOnRestore(String email) {
            // 準備：メールアドレスだけを変えた注文のスナップショット
            var snapshot = snapshotWithEmail(email);

            // 操作と確認：業務のエラー INVALID_EMAIL で拒否される
            assertBusinessError(() -> Order.restore(snapshot), OrderErrorCode.INVALID_EMAIL);
        }

        @Test
        @DisplayName("メールアドレスのない注文は、復元でも拒否される")
        void rejectsMissingEmailOnRestore() {
            // 準備：メールアドレスのない注文のスナップショット
            var snapshot = snapshotWithEmail(null);

            // 操作と確認：業務のエラー INVALID_EMAIL で拒否される
            assertBusinessError(() -> Order.restore(snapshot), OrderErrorCode.INVALID_EMAIL);
        }

        @Test
        @DisplayName("上限を 1 文字超える 255 文字のメールアドレスの注文は、復元でも拒否される")
        void rejectsEmailOverMaxLengthOnRestore() {
            // 準備：形が正しく、255 文字のメールアドレスの注文のスナップショット
            var snapshot = snapshotWithEmail(emailOfLength(255));

            // 操作と確認：業務のエラー INVALID_EMAIL で拒否される
            assertBusinessError(() -> Order.restore(snapshot), OrderErrorCode.INVALID_EMAIL);
        }

        @Test
        @DisplayName("上限の 254 文字ちょうどのメールアドレスの注文は、復元できる")
        void restoresEmailAtMaxLength() {
            // 準備：形が正しく、254 文字のメールアドレスの注文のスナップショット
            var email = emailOfLength(254);
            var snapshot = snapshotWithEmail(email);

            // 操作：復元する
            var order = Order.restore(snapshot);

            // 確認：渡したメールアドレスのまま
            assertThat(order.email()).isEqualTo(email);
        }

        /**
         * メールアドレスだけを変えた、注文のスナップショットを作る。
         *
         * @param email メールアドレス
         * @return スナップショット
         */
        private Order.Snapshot snapshotWithEmail(@Nullable String email) {
            var base = place(quote()).toSnapshot();
            return new Order.Snapshot(base.id(), base.orderNumber(), base.checkoutId(), base.memberId(), base.orderedAt(),
                    base.status(), base.lines(), base.amounts(), base.shippingTax(), email, base.shippingAddress(),
                    base.buyerProfile(),
                    base.cancellationRequest(), base.cancellation(), base.version());
        }

        /**
         * 指定のメールアドレスで、注文できる見積もりから注文を作る。
         *
         * @param email メールアドレス
         * @return 注文
         */
        private Order placeWithEmail(@Nullable String email) {
            return Order.place(UUID.randomUUID(), OrderNumber.of("20261008-000001"), UUID.randomUUID(), UUID.randomUUID(),
                    ORDERED_AT, quote(), email, ADDRESS, BuyerProfile.UNKNOWN);
        }

        /**
         * 形が正しく、指定の文字数のメールアドレスを作る。
         *
         * <p>ドメインを {@code @example.com} にし、ローカル部の長さで文字数を合わせる。
         *
         * @param length 文字数
         * @return メールアドレス
         */
        private String emailOfLength(int length) {
            var domain = "@example.com";
            return "a".repeat(length - domain.length()) + domain;
        }
    }

    @Nested
    @DisplayName("状態と取消の情報の整合（復元）")
    class Consistency {

        /**
         * キャンセルの申請の決定日時と、キャンセルの日時
         */
        private static final Instant DECIDED_AT = Instant.parse("2026-10-09T01:23:45Z");

        /**
         * 運営者のキャンセルの理由
         */
        private static final String REASON = "在庫の不足のため";

        @ParameterizedTest(name = "「{0}」申請 {1}、取消 {2}")
        @CsvSource({
                "PENDING_PAYMENT, NONE, false",
                // 申請していない注文と、申請を却下して受付に戻した注文
                "ACCEPTED, NONE, false", "ACCEPTED, REJECTED, false",
                "CANCELLATION_REQUESTED, PENDING, false",
                // 受付から出荷するので、申請がないか却下済み
                "SHIPPED, NONE, false", "SHIPPED, REJECTED, false",
                // 支払期限切れ・運営者のキャンセル（申請なし、却下の後）、申請の承認・申請中の運営者のキャンセル（承認）
                "CANCELLED, NONE, true", "CANCELLED, APPROVED, true", "CANCELLED, REJECTED, true"})
        @DisplayName("状態と取消の情報が整合すれば、復元でき、同じ値を持つ")
        void restoresConsistentItems(OrderStatus status, String request, boolean cancelled) {
            // 準備：整合する注文のスナップショット
            var snapshot = snapshotOf(status, request, cancelled);

            // 操作：復元する
            var order = Order.restore(snapshot);

            // 確認：全ての項目が同じ
            assertThat(order.toSnapshot()).usingRecursiveComparison().isEqualTo(snapshot);
        }

        @ParameterizedTest(name = "「{0}」申請 {1}、取消 {2}")
        @CsvSource({
                // 取消のない状態は、取消を持たない
                "PENDING_PAYMENT, NONE, true",
                "ACCEPTED, NONE, true", "ACCEPTED, REJECTED, true",
                "CANCELLATION_REQUESTED, PENDING, true",
                "SHIPPED, NONE, true", "SHIPPED, REJECTED, true",
                // 支払待ちは申請できない
                "PENDING_PAYMENT, PENDING, false", "PENDING_PAYMENT, APPROVED, false", "PENDING_PAYMENT, REJECTED, false",
                "PENDING_PAYMENT, PENDING, true", "PENDING_PAYMENT, APPROVED, true", "PENDING_PAYMENT, REJECTED, true",
                // 受付と出荷済みの申請は、ないか却下済み
                "ACCEPTED, PENDING, false", "ACCEPTED, APPROVED, false", "ACCEPTED, PENDING, true", "ACCEPTED, APPROVED, true",
                "SHIPPED, PENDING, false", "SHIPPED, APPROVED, false", "SHIPPED, PENDING, true", "SHIPPED, APPROVED, true",
                // 申請中は、決定していない申請を持つ
                "CANCELLATION_REQUESTED, NONE, false", "CANCELLATION_REQUESTED, APPROVED, false",
                "CANCELLATION_REQUESTED, REJECTED, false",
                "CANCELLATION_REQUESTED, NONE, true", "CANCELLATION_REQUESTED, APPROVED, true",
                "CANCELLATION_REQUESTED, REJECTED, true",
                // キャンセルは取消を持ち、申請は決定済み
                "CANCELLED, NONE, false", "CANCELLED, PENDING, false", "CANCELLED, APPROVED, false",
                "CANCELLED, REJECTED, false", "CANCELLED, PENDING, true"})
        @DisplayName("状態と取消の情報が整合しなければ、復元できず、メッセージに値が出ない")
        void rejectsInconsistentItems(OrderStatus status, String request, boolean cancelled) {
            // 準備：整合しない注文のスナップショット
            var snapshot = snapshotOf(status, request, cancelled);

            // 操作と確認：例外になり、理由・日時・メールアドレスがない
            assertThatThrownBy(() -> Order.restore(snapshot))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining(REASON)
                    .hasMessageNotContaining(DECIDED_AT.toString())
                    .hasMessageNotContaining(EMAIL);
        }

        /**
         * 状態と取消の情報を指定した、注文のスナップショットを作る。
         *
         * <p>申請の理由と取消の理由には {@link #REASON} を入れる。
         *
         * @param status    状態
         * @param request   申請の結果（{@code NONE} なら申請なし）
         * @param cancelled 取消があるか
         * @return スナップショット
         */
        private Order.Snapshot snapshotOf(OrderStatus status, String request, boolean cancelled) {
            var base = place(quote()).toSnapshot();
            var cancellationRequest = request.equals("NONE") ? null
                    : requestOf(CancellationRequest.Result.valueOf(request));
            var cancellation = cancelled ? new Cancellation(DECIDED_AT, CancellationKind.OPERATOR, REASON, null) : null;
            return new Order.Snapshot(base.id(), base.orderNumber(), base.checkoutId(), base.memberId(), base.orderedAt(),
                    status, base.lines(), base.amounts(), base.shippingTax(), base.email(), base.shippingAddress(),
                    base.buyerProfile(),
                    cancellationRequest, cancellation, base.version());
        }

        /**
         * 指定の結果の申請を作る。
         *
         * @param result 結果
         * @return 申請。申請中なら決定の日時がない
         */
        private CancellationRequest requestOf(CancellationRequest.Result result) {
            var decidedAt = result == CancellationRequest.Result.PENDING ? null : DECIDED_AT;
            return new CancellationRequest(ORDERED_AT, REASON, result, decidedAt, null);
        }
    }

    @Nested
    @DisplayName("遷移の後の復元")
    class RestoreAfterTransitions {

        /**
         * 遷移の時刻
         */
        private static final Instant LATER = ORDERED_AT.plus(Duration.ofHours(1));

        /**
         * 申請を決定し、キャンセルする運営者
         */
        private static final Operator OPERATOR = new Operator(UUID.randomUUID(), "管理 花子");

        /**
         * 申請と運営者のキャンセルの理由
         */
        private static final String REASON = "在庫の不足のため";

        /**
         * 遷移のメソッドをたどって、状態と取消の情報の整合の表の各行に至る注文を作る経路
         *
         * @return 経路の名前、至る状態、注文を作る処理
         */
        static Stream<Arguments> transitionPaths() {
            return Stream.of(
                    Arguments.of("注文の作成", OrderStatus.PENDING_PAYMENT, (Supplier<Order>) () -> place(quote())),
                    Arguments.of("支払の受付", OrderStatus.ACCEPTED, (Supplier<Order>) RestoreAfterTransitions::accepted),
                    Arguments.of("申請の却下", OrderStatus.ACCEPTED, (Supplier<Order>) RestoreAfterTransitions::rejected),
                    Arguments.of("キャンセルの申請", OrderStatus.CANCELLATION_REQUESTED,
                            (Supplier<Order>) RestoreAfterTransitions::requested),
                    Arguments.of("受付からの出荷", OrderStatus.SHIPPED,
                            (Supplier<Order>) () -> after(accepted(), RestoreAfterTransitions::ship)),
                    Arguments.of("却下の後の出荷", OrderStatus.SHIPPED,
                            (Supplier<Order>) () -> after(rejected(), RestoreAfterTransitions::ship)),
                    Arguments.of("支払の期限切れ", OrderStatus.CANCELLED,
                            (Supplier<Order>) () -> after(place(quote()), order -> order.expirePayment(LATER))),
                    Arguments.of("申請の承認", OrderStatus.CANCELLED,
                            (Supplier<Order>) () -> after(requested(), order -> order.approveCancellation(OPERATOR, LATER))),
                    Arguments.of("受付からの運営者のキャンセル", OrderStatus.CANCELLED,
                            (Supplier<Order>) () -> after(accepted(),
                                    order -> order.cancelByOperator(REASON, null, false, OPERATOR, LATER))),
                    Arguments.of("申請中の運営者のキャンセル", OrderStatus.CANCELLED,
                            (Supplier<Order>) () -> after(requested(),
                                    order -> order.cancelByOperator(REASON, null, false, OPERATOR, LATER))),
                    Arguments.of("却下の後の運営者のキャンセル", OrderStatus.CANCELLED,
                            (Supplier<Order>) () -> after(rejected(),
                                    order -> order.cancelByOperator(REASON, null, false, OPERATOR, LATER))),
                    Arguments.of("出荷済みの返送の運営者のキャンセル", OrderStatus.CANCELLED,
                            (Supplier<Order>) () -> after(after(accepted(), RestoreAfterTransitions::ship),
                                    order -> order.cancelByOperator(REASON, true, true, OPERATOR, LATER))),
                    Arguments.of("却下の後に出荷した返送の運営者のキャンセル", OrderStatus.CANCELLED,
                            (Supplier<Order>) () -> after(after(rejected(), RestoreAfterTransitions::ship),
                                    order -> order.cancelByOperator(REASON, false, true, OPERATOR, LATER))));
        }

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("transitionPaths")
        @DisplayName("遷移のメソッドで作った注文のスナップショットは、そのまま復元でき、同じ値を持つ")
        void restoresAfterTransitions(String path, OrderStatus expected, Supplier<Order> transition) {
            // 準備：遷移のメソッドをたどった注文のスナップショット
            var snapshot = transition.get().toSnapshot();

            // 確認（前提）：経路が狙った状態に至っている
            assertThat(snapshot.status()).isEqualTo(expected);

            // 操作：復元する
            var restored = Order.restore(snapshot);

            // 確認：全ての項目が同じ
            assertThat(restored.toSnapshot()).usingRecursiveComparison().isEqualTo(snapshot);
        }

        /**
         * 注文に遷移を行う。
         *
         * @param order      注文
         * @param transition 遷移
         * @return 遷移した注文
         */
        private static Order after(Order order, Consumer<Order> transition) {
            transition.accept(order);
            return order;
        }

        /**
         * 支払を受け付けた注文を作る。
         *
         * @return 「受付」の注文
         */
        private static Order accepted() {
            return after(place(quote()), order -> order.acceptPayment(LATER));
        }

        /**
         * キャンセルを申請した注文を作る。
         *
         * @return 「キャンセル申請中」の注文
         */
        private static Order requested() {
            return after(accepted(), order -> order.requestCancellation(REASON, LATER));
        }

        /**
         * キャンセルの申請を却下した注文を作る。
         *
         * @return 却下した申請を持つ「受付」の注文
         */
        private static Order rejected() {
            return after(requested(), order -> order.rejectCancellation(OPERATOR, LATER));
        }

        /**
         * 注文を出荷する。
         *
         * @param order 「受付」の注文
         */
        private static void ship(Order order) {
            order.ship(Carrier.YAMATO, "123456789012", LATER);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 注文成立のイベントだけが登録されていることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>登録されたイベントが 1 件だけ</li>
     *   <li>種類が {@link OrderStatusChanged}</li>
     *   <li>注文 ID がこの注文</li>
     *   <li>前の状態がない（成立）</li>
     *   <li>後の状態が「支払待ち」</li>
     * </ul>
     *
     * @param order 確認する注文
     */
    private static void assertPlacedEventOnly(Order order) {
        assertThat(order.pullEvents()).singleElement().isInstanceOfSatisfying(OrderStatusChanged.class, event -> {
            assertThat(event.orderId()).isEqualTo(order.id());
            assertThat(event.from()).isNull();
            assertThat(event.to()).isEqualTo(OrderStatusChanged.Status.PENDING_PAYMENT);
        });
    }

    /**
     * 明細が、注文できる見積もり（{@link #quote()}）の品目の写しであることを確認する。
     *
     * <p>確認すること（期待値は定数）：
     * <ul>
     *   <li>行番号が 1</li>
     *   <li>バリアント ID</li>
     *   <li>品番、バリアント品番</li>
     *   <li>商品名</li>
     *   <li>軸の値（並びを含む）</li>
     *   <li>税抜単価、税率、税込単価</li>
     *   <li>数量、小計</li>
     * </ul>
     *
     * @param line      確認する明細
     * @param variantId 見積もりの品目のバリアント ID（実行ごとに作るので、定数にできない）
     */
    private static void assertLineCopiedFromQuote(OrderLine line, UUID variantId) {
        assertThat(line.lineNo()).isEqualTo(1);
        assertThat(line.variantId()).isEqualTo(variantId);
        assertThat(line.productCode()).isEqualTo(PRODUCT_CODE);
        assertThat(line.variantCode()).isEqualTo(VARIANT_CODE);
        assertThat(line.productName()).isEqualTo(PRODUCT_NAME);
        assertThat(line.axisValues()).containsExactlyElementsOf(AXIS_VALUES);
        assertThat(line.unitPriceExclTax()).isEqualTo(UNIT_PRICE_EXCL_TAX);
        assertThat(line.ratePercent()).isEqualByComparingTo(RATE_PERCENT);
        assertThat(line.unitPriceInclTax()).isEqualTo(UNIT_PRICE_INCL_TAX);
        assertThat(line.quantity()).isEqualTo(QUANTITY);
        assertThat(line.subtotal()).isEqualTo(SUBTOTAL);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 注文を作る。
     *
     * @param quote 見積もり
     * @return 注文
     */
    private static Order place(Quote quote) {
        return Order.place(UUID.randomUUID(), OrderNumber.of("20261008-482915"), UUID.randomUUID(), UUID.randomUUID(), ORDERED_AT,
                quote, EMAIL, ADDRESS, BuyerProfile.UNKNOWN);
    }

    /**
     * T シャツ 2 枚の、注文できる見積もりを作る。
     *
     * <p>送料は 1,000 円、総額は 6,940 円（うち消費税 630 円）。
     *
     * @return 見積もり
     */
    private static Quote quote() {
        var item = QuoteItem.of(tShirt(), QUANTITY, 10, RATE_PERCENT, UNIT_PRICE_INCL_TAX);
        var amounts = new Amounts(SUBTOTAL, Money.of(1000), Money.of(6940),
                List.of(new TaxAmount(RATE_PERCENT, Money.of(6940), Money.of(630))));
        return new Quote(List.of(item), amounts, Money.of(90), true);
    }

    /**
     * 指定のバリアント品番の T シャツを 1 枚ずつ、指定の並びで持つ、注文できる見積もりを作る。
     *
     * <p>行番号の確認にだけ使うので、金額の内訳は品目と合わせない。
     *
     * @param variantCodes バリアント品番の並び
     * @return 見積もり
     */
    private static Quote quoteOf(List<String> variantCodes) {
        var items = variantCodes.stream()
                .map(code -> QuoteItem.of(new VariantInfo(UUID.randomUUID(), UUID.randomUUID(), PRODUCT_CODE, code, PRODUCT_NAME,
                        AXIS_VALUES, UNIT_PRICE_EXCL_TAX, UUID.randomUUID(), null, true, false), 1, 10, RATE_PERCENT, UNIT_PRICE_INCL_TAX))
                .toList();
        return new Quote(items, quote().amounts(), quote().shippingTax(), true);
    }

    /**
     * 在庫が 0 で購入できない T シャツ 1 枚の、注文できない見積もりを作る。
     *
     * <p>購入できない品目は合計に含めないので、金額は 0。
     *
     * @return 見積もり
     */
    private static Quote quoteWithOutOfStockItem() {
        var item = QuoteItem.of(tShirt(), 1, 0, RATE_PERCENT, UNIT_PRICE_INCL_TAX);
        return new Quote(List.of(item), new Amounts(Money.ZERO, Money.ZERO, Money.ZERO, List.of()), Money.ZERO, false);
    }

    /**
     * T シャツ（白・M）のバリアントを、定数から作る。
     *
     * @return バリアント
     */
    private static VariantInfo tShirt() {
        return new VariantInfo(UUID.randomUUID(), UUID.randomUUID(), PRODUCT_CODE, VARIANT_CODE, PRODUCT_NAME,
                AXIS_VALUES, UNIT_PRICE_EXCL_TAX, UUID.randomUUID(), null, true, false);
    }
}
