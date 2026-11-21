package com.ecsite.module.order.infrastructure.persistence;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.model.value.Money;
import com.ecsite.common.model.value.Operator;
import com.ecsite.common.persistence.AdvisoryLockSpace;
import com.ecsite.common.persistence.AdvisoryLocks;
import com.ecsite.module.order.domain.order.Order;
import com.ecsite.module.order.domain.order.OrderStatus;
import com.ecsite.module.order.domain.order.TestAreas;
import com.ecsite.module.order.domain.order.port.OrderRepository;
import com.ecsite.module.order.domain.order.vo.BuyerProfile;
import com.ecsite.module.order.domain.order.vo.Cancellation;
import com.ecsite.module.order.domain.order.vo.CancellationKind;
import com.ecsite.module.order.domain.order.vo.CancellationRequest;
import com.ecsite.module.order.domain.order.vo.Gender;
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
import com.ecsite.support.async.AsyncEvents;
import com.ecsite.support.config.IntegrationTest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 注文のリポジトリの統合テスト
 *
 * <p>保存と再構築、注文番号と注文手続きの一意性、更新、行のロックと会員のロックを確認する。注文の状態の変化の規則は、注文の単体テストが確認する。
 *
 * <p>保存した注文の期待値は、準備に使った定数から作り、項目ごとに確認する（保存の前の注文から取り出さない）。
 * 行のロックを確認するテスト以外は、テストごとにロールバックする。
 */
@IntegrationTest
@RecordApplicationEvents
@Transactional
class JooqOrderRepositoryTest {

    // ============================== 定数 ==============================

    /**
     * 注文日時
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 注文番号の日付
     *
     * <p>ロールバックしないテストが作る注文と、他のテストの注文番号が重ならない日付。
     */
    private static final String ORDER_NUMBER_DATE = "19991231";

    /**
     * キャンセルの申請の理由
     */
    private static final String REQUEST_REASON = "サイズが合わない";

    /**
     * 受付にした日時（注文の 1 分後）
     */
    private static final Instant ACCEPTED_AT = ORDERED_AT.plusSeconds(60);

    /**
     * キャンセルを申請した日時（注文の 1 時間後）
     */
    private static final Instant REQUESTED_AT = ORDERED_AT.plusSeconds(3600);

    /**
     * 申請を承認した日時（注文の 2 時間後）
     */
    private static final Instant DECIDED_AT = ORDERED_AT.plusSeconds(7200);

    /**
     * 上限の 500 文字の理由（絵文字のサロゲートペアを含む）
     */
    private static final String MAX_REASON = "理由😀".repeat(166) + "理由";

    /**
     * 注文した会員の ID（実行ごとに作るが、実行の中では同じ）
     */
    private static final UUID MEMBER_ID = UUID.randomUUID();

    /**
     * T シャツのバリアント ID
     */
    private static final UUID SHIRT_VARIANT_ID = UUID.randomUUID();

    /**
     * お茶のバリアント ID
     */
    private static final UUID TEA_VARIANT_ID = UUID.randomUUID();

    /**
     * 標準の税率（%）
     */
    private static final BigDecimal STANDARD_RATE = BigDecimal.TEN;

    /**
     * 軽減の税率（%）
     */
    private static final BigDecimal REDUCED_RATE = new BigDecimal("8");

    /**
     * T シャツの品番
     */
    private static final String SHIRT_PRODUCT_CODE = "TS-001";

    /**
     * T シャツ（白・M）のバリアント品番
     */
    private static final String SHIRT_VARIANT_CODE = "TS-001-WM";

    /**
     * T シャツの商品名
     */
    private static final String SHIRT_NAME = "T シャツ";

    /**
     * T シャツ（白・M）の軸の値
     */
    private static final List<AxisValue> SHIRT_AXES = List.of(new AxisValue("色", "白"), new AxisValue("サイズ", "M"));

    /**
     * T シャツの税抜単価
     */
    private static final Money SHIRT_PRICE_EXCL_TAX = Money.of(2700);

    /**
     * T シャツの税込単価（10%）
     */
    private static final Money SHIRT_PRICE_INCL_TAX = Money.of(2970);

    /**
     * お茶の品番（バリアント品番も同じ）
     */
    private static final String TEA_CODE = "TEA-1";

    /**
     * お茶の商品名
     */
    private static final String TEA_NAME = "お茶";

    /**
     * お茶の税抜単価
     */
    private static final Money TEA_PRICE_EXCL_TAX = Money.of(1234);

    /**
     * お茶の税込単価（8%）
     */
    private static final Money TEA_PRICE_INCL_TAX = Money.of(1332);

    /**
     * 上限の注文の商品の品番（バリアント品番も同じ）
     */
    private static final String MAX_CODE = "MAX-1";

    /**
     * 上限の注文の商品名
     */
    private static final String MAX_NAME = "高価な商品";

    /**
     * 上限の税抜単価
     */
    private static final Money MAX_PRICE_EXCL_TAX = Money.of(9_999_999);

    /**
     * 上限の税抜単価の税込単価（10%）
     */
    private static final Money MAX_PRICE_INCL_TAX = Money.of(10_999_998);

    /**
     * 上限の数量
     */
    private static final int MAX_QUANTITY = 10;

    /**
     * 上限の注文の小計（税込単価 × 上限の数量）
     */
    private static final Money MAX_SUBTOTAL = Money.of(109_999_980);

    /**
     * 標準の注文のメールアドレス
     */
    private static final String EMAIL = "taro@example.com";

    /**
     * 標準の注文のお届け先
     */
    private static final ShippingAddress ADDRESS =
            ShippingAddress.of("山田 太郎", "100-0001", TestAreas.CHIYODA, "千代田 1-1", "03-1234-5678");

    /**
     * 標準の注文の、注文時の年齢と性別
     */
    private static final BuyerProfile BUYER_PROFILE = new BuyerProfile(36, Gender.FEMALE);

    /**
     * 標準の注文の明細（T シャツ 1 枚（10%）とお茶 1 個（8%））
     */
    private static final List<OrderLine> LINES = List.of(
            new OrderLine(1, SHIRT_VARIANT_ID, SHIRT_PRODUCT_CODE, SHIRT_VARIANT_CODE, SHIRT_NAME, SHIRT_AXES,
                    SHIRT_PRICE_EXCL_TAX, STANDARD_RATE, SHIRT_PRICE_INCL_TAX, 1, SHIRT_PRICE_INCL_TAX),
            new OrderLine(2, TEA_VARIANT_ID, TEA_CODE, TEA_CODE, TEA_NAME, List.of(),
                    TEA_PRICE_EXCL_TAX, REDUCED_RATE, TEA_PRICE_INCL_TAX, 1, TEA_PRICE_INCL_TAX));

    /**
     * 標準の注文の金額の内訳
     */
    private static final Amounts AMOUNTS = new Amounts(Money.of(4302), Money.of(1000), Money.of(5302), List.of(
            new TaxAmount(BigDecimal.TEN, Money.of(3970), Money.of(360)),
            new TaxAmount(new BigDecimal("8"), Money.of(1332), Money.of(98))));

    /**
     * 注文の送料の消費税（送料 1,000 円・標準税率の消費税額）
     */
    private static final Money SHIPPING_TAX = Money.of(90);

    /**
     * 上限の注文のメールアドレス（254 文字）
     */
    private static final String MAX_EMAIL = "a".repeat(254 - "@example.com".length()) + "@example.com";

    /**
     * 上限の注文のお届け先（絵文字を含む 50 文字の氏名と、200 文字の住所）
     */
    private static final ShippingAddress MAX_ADDRESS =
            ShippingAddress.of("山田" + "😀".repeat(48), "100-0001", TestAreas.CHIYODA, "千".repeat(200), "03-1234-5678");

    /**
     * 上限の注文の、注文時の年齢と性別（120 歳）
     */
    private static final BuyerProfile MAX_BUYER_PROFILE = new BuyerProfile(120, Gender.OTHER);

    /**
     * 上限の注文の明細（税抜単価 9,999,999 円（税込 10,999,998 円）を数量 10 個）
     */
    private static final OrderLine MAX_LINE = new OrderLine(1, SHIRT_VARIANT_ID, MAX_CODE, MAX_CODE, MAX_NAME, List.of(),
            MAX_PRICE_EXCL_TAX, STANDARD_RATE, MAX_PRICE_INCL_TAX, MAX_QUANTITY, MAX_SUBTOTAL);

    /**
     * 上限の注文の金額の内訳
     */
    private static final Amounts MAX_AMOUNTS = new Amounts(Money.of(109_999_980), Money.of(1000), Money.of(110_000_980), List.of(
            new TaxAmount(BigDecimal.TEN, Money.of(110_000_980), Money.of(10_000_089))));

    // ============================== Bean ==============================

    @Autowired
    OrderRepository repository;

    @Autowired
    DSLContext dsl;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    ApplicationEvents events;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("保存と再構築")
    class InsertAndRestore {

        @Test
        @DisplayName("注文を保存し、明細・金額・お届け先・注文時の年齢と性別を含めて再構築できる")
        void savesAndRestoresOrder() {
            // 準備：2 つの税率の品目を持つ注文
            var checkoutId = UUID.randomUUID();
            var orderNumber = orderNumber();
            var order = order(checkoutId, orderNumber);

            // 操作：保存する
            assertThat(repository.insert(order)).isTrue();

            // 確認：読み直した注文の全ての項目が、準備の定数のまま
            var restored = repository.findById(order.id()).orElseThrow();
            assertPlacedStandardOrder(restored.toSnapshot(), order.id(), checkoutId, orderNumber);
        }

        @Test
        @DisplayName("DC-484-4 送料の消費税が保存されていない注文（列の追加の前に成立した注文）は、null のまま読み直せる")
        void restoresNullShippingTax() {
            // 準備：注文を保存し、送料の消費税の列を null にする（列の追加の前に成立した注文の状態）
            var order = order(UUID.randomUUID(), orderNumber());
            repository.insert(order);
            dsl.update(SALES_ORDER).setNull(SALES_ORDER.SHIPPING_TAX).where(SALES_ORDER.ID.eq(order.id())).execute();

            // 操作：読み直す
            var restored = repository.findById(order.id()).orElseThrow();

            // 確認：送料の消費税が null（0 に変わらない）
            assertThat(restored.toSnapshot().shippingTax()).isNull();
        }

        @Test
        @DisplayName("注文を保存すると、注文の状態の変化（支払待ち）のイベントが発行される")
        void publishesPlacedEvent() {
            // 準備：注文
            var order = order(UUID.randomUUID(), orderNumber());

            // 操作：保存する
            repository.insert(order);

            // 確認：この注文のイベントだけが発行される
            assertThat(events.stream(OrderStatusChanged.class)).extracting(OrderStatusChanged::orderId).containsExactly(order.id());
        }
    }

    @Nested
    @DisplayName("注文番号")
    class OrderNumbers {

        @Test
        @DisplayName("BR-10 注文番号が重複したら保存せずに false を返す（呼ぶ側が番号を再生成する）")
        void returnsFalseForDuplicateOrderNumber() {
            // 準備：同じ番号の注文を先に保存する
            var number = orderNumber();
            repository.insert(order(UUID.randomUUID(), number));
            var duplicate = order(UUID.randomUUID(), number);

            // 操作と確認：false を返す
            assertThat(repository.insert(duplicate)).isFalse();

            // 確認：重複した注文は保存されていない
            assertThat(repository.findById(duplicate.id())).isEmpty();
        }

        @Test
        @DisplayName("BR-10 注文番号が重複して保存しなかった注文は、イベントを発行しない")
        void doesNotPublishForDuplicateOrderNumber() {
            // 準備：同じ番号の注文を先に保存する
            var number = orderNumber();
            repository.insert(order(UUID.randomUUID(), number));

            // 操作：同じ番号の注文を保存する
            repository.insert(order(UUID.randomUUID(), number));

            // 確認：イベントは先に保存した注文の 1 件だけ
            assertThat(events.stream(OrderStatusChanged.class)).hasSize(1);
        }

        @Test
        @DisplayName("注文番号が使われているかを、保存した注文の番号だけ true で確認できる")
        void checksOrderNumberExistence() {
            // 準備：注文を保存する
            var number = orderNumber();
            repository.insert(order(UUID.randomUUID(), number));

            // 操作と確認：保存した番号は true、別の番号は false
            assertThat(repository.existsByOrderNumber(OrderNumber.of(number))).isTrue();
            assertThat(repository.existsByOrderNumber(OrderNumber.of(otherThan(number)))).isFalse();
        }
    }

    @Nested
    @DisplayName("注文手続きの注文")
    class ByCheckout {

        @Test
        @DisplayName("AC-91 注文手続きの注文を引ける。注文のない注文手続きは空")
        void findsOrderByCheckout() {
            // 準備：注文手続きの注文を保存する
            var checkoutId = UUID.randomUUID();
            var order = order(checkoutId, orderNumber());
            repository.insert(order);

            // 操作と確認：その注文手続きでは注文 ID、別の注文手続きでは空
            assertThat(repository.findIdByCheckoutId(checkoutId)).contains(order.id());
            assertThat(repository.findIdByCheckoutId(UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("AC-91 注文手続き 1 つにつき、注文は 1 つしか保存できない")
        void rejectsSecondOrderForCheckout() {
            // 準備：注文手続きの注文を保存する
            var checkoutId = UUID.randomUUID();
            repository.insert(order(checkoutId, orderNumber()));

            // 操作と確認：同じ注文手続きの、別の番号の注文は一意性の違反
            assertThatThrownBy(() -> repository.insert(order(checkoutId, orderNumber()))).isInstanceOf(DuplicateKeyException.class);
        }
    }

    @Nested
    @DisplayName("更新")
    class Update {

        @Test
        @DisplayName("注文を受付に変えて更新すると、状態が受付になり、バージョンが 1 上がり、他の項目は変わらない")
        void updatesStatusAndVersion() {
            // 準備：保存した注文を読み、受付にする
            var checkoutId = UUID.randomUUID();
            var orderNumber = orderNumber();
            var order = order(checkoutId, orderNumber);
            repository.insert(order);
            var loaded = repository.findById(order.id()).orElseThrow();
            loaded.acceptPayment(ACCEPTED_AT);

            // 操作：更新する
            repository.update(loaded);

            // 確認：状態とバージョンだけが変わる
            assertAcceptedUpdate(repository.findById(order.id()).orElseThrow().toSnapshot(), order.id(), checkoutId, orderNumber);
        }

        @Test
        @DisplayName("注文を受付に変えて更新すると、状態の変化のイベントが発行される")
        void publishesAcceptedEvent() {
            // 準備：保存した注文を読み、受付にする
            var order = order(UUID.randomUUID(), orderNumber());
            repository.insert(order);
            var loaded = repository.findById(order.id()).orElseThrow();
            loaded.acceptPayment(ACCEPTED_AT);

            // 操作：更新する
            repository.update(loaded);

            // 確認：保存のときと、受付のときの 2 件
            assertThat(events.stream(OrderStatusChanged.class)).hasSize(2);
        }

        @Test
        @DisplayName("キャンセルの申請を更新すると、申請を含めて再構築できる")
        void updatesCancellationRequest() {
            // 準備：受付の注文を読み、キャンセルを申請する
            var orderId = acceptedOrder();
            var requested = repository.findById(orderId).orElseThrow();
            requested.requestCancellation(REQUEST_REASON, REQUESTED_AT);

            // 操作：更新する
            repository.update(requested);

            // 確認：申請が、日時と理由と未決の結果で残る
            assertThat(repository.findById(orderId).orElseThrow().toSnapshot().cancellationRequest())
                    .isEqualTo(new CancellationRequest(REQUESTED_AT, REQUEST_REASON, CancellationRequest.Result.PENDING,
                            null, null));
        }

        @Test
        @DisplayName("申請の承認によるキャンセルを更新すると、承認した運営者とキャンセルの記録を含めて再構築できる")
        void updatesApprovedCancellation() {
            // 準備：申請中の注文を行ロックして読み、運営者が承認する
            var orderId = acceptedOrder();
            var requested = repository.findById(orderId).orElseThrow();
            requested.requestCancellation(REQUEST_REASON, REQUESTED_AT);
            repository.update(requested);
            var operator = new Operator(UUID.randomUUID(), "管理者 花子");
            var pending = repository.lockById(orderId).orElseThrow();
            assertThat(pending.approveCancellation(operator, DECIDED_AT)).isTrue();

            // 操作：更新する
            repository.update(pending);

            // 確認：キャンセルで、申請の承認とキャンセルの記録が残る
            assertApprovedCancellation(repository.findById(orderId).orElseThrow().toSnapshot(), operator);
        }

        @Test
        @DisplayName("同じバージョンを読んだ注文を 2 回更新すると、2 回目は同時の更新のエラーで、状態とイベントを変えない")
        void rejectsStaleVersion() {
            // 準備：保存した注文を 2 つの写しで読み、どちらも受付にして、1 つ目を更新する
            var order = order(UUID.randomUUID(), orderNumber());
            repository.insert(order);
            var first = repository.findById(order.id()).orElseThrow();
            var stale = repository.findById(order.id()).orElseThrow();
            first.acceptPayment(ACCEPTED_AT);
            stale.acceptPayment(ACCEPTED_AT);
            repository.update(first);

            // 操作と確認：古いバージョンの写しの更新は、業務のエラー CONCURRENT_MODIFICATION で拒否される
            assertBusinessError(() -> repository.update(stale), CommonErrorCode.CONCURRENT_MODIFICATION);

            // 確認：バージョンは 1 回目の更新の 1 のままで、イベントは保存と 1 回目の更新の 2 件だけ
            assertThat(repository.findById(order.id()).orElseThrow().version()).isEqualTo(1);
            assertThat(events.stream(OrderStatusChanged.class).filter(event -> event.orderId().equals(order.id()))).hasSize(2);
        }
    }

    @Nested
    @DisplayName("上限の値")
    class MaxValues {

        @Test
        @DisplayName("domain の上限の値（氏名・住所・メールアドレス・年齢・数量・単価）の注文を、そのまま保存して再構築できる")
        void savesMaxValues() {
            // 準備：各項目が domain の上限の注文
            var checkoutId = UUID.randomUUID();
            var orderNumber = orderNumber();
            var order = maxOrder(checkoutId, orderNumber);

            // 操作：保存する
            assertThat(repository.insert(order)).isTrue();

            // 確認：読み直した注文の全ての項目が、上限の定数のまま
            var restored = repository.findById(order.id()).orElseThrow().toSnapshot();
            assertMaxContent(restored, order.id(), checkoutId, orderNumber);
            assertThat(restored.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        }

        @Test
        @DisplayName("上限の 500 文字の申請と運営者のキャンセルの理由と、255 文字の運営者名を、そのまま更新して再構築できる")
        void updatesMaxReasons() {
            // 準備：上限の注文を受付にし、500 文字の理由で申請し、500 文字の理由と 255 文字の名前の運営者がキャンセルする
            var checkoutId = UUID.randomUUID();
            var orderNumber = orderNumber();
            var order = maxOrder(checkoutId, orderNumber);
            repository.insert(order);
            var loaded = repository.findById(order.id()).orElseThrow();
            loaded.acceptPayment(ACCEPTED_AT);
            repository.update(loaded);
            var requested = repository.findById(order.id()).orElseThrow();
            requested.requestCancellation(MAX_REASON, REQUESTED_AT);
            repository.update(requested);
            var operator = new Operator(UUID.randomUUID(), "管".repeat(255));
            var cancelled = repository.findById(order.id()).orElseThrow();
            cancelled.cancelByOperator(MAX_REASON, true, false, operator, DECIDED_AT);

            // 操作：更新する
            repository.update(cancelled);

            // 確認：成立の時点の項目が上限の定数のままで、申請とキャンセルが 500 文字の理由と 255 文字の運営者名を持つ
            var restored = repository.findById(order.id()).orElseThrow().toSnapshot();
            assertMaxContent(restored, order.id(), checkoutId, orderNumber);
            assertThat(restored.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(restored.cancellationRequest()).isEqualTo(new CancellationRequest(REQUESTED_AT, MAX_REASON,
                    CancellationRequest.Result.APPROVED, DECIDED_AT, operator));
            assertThat(restored.cancellation())
                    .isEqualTo(new Cancellation(DECIDED_AT, CancellationKind.OPERATOR, MAX_REASON, operator, true));
        }
    }

    @Nested
    @DisplayName("行のロック")
    class RowLock {

        @Test
        @DisplayName("注文を行ロックして取得すると、保存した注文を再構築して返す")
        void locksOrderById() {
            // 準備：注文を保存する
            var checkoutId = UUID.randomUUID();
            var orderNumber = orderNumber();
            var order = order(checkoutId, orderNumber);
            repository.insert(order);

            // 操作と確認：全ての項目が、準備の定数のまま
            assertPlacedStandardOrder(repository.lockById(order.id()).orElseThrow().toSnapshot(), order.id(), checkoutId, orderNumber);
        }

        @Test
        @DisplayName("ない注文を行ロックして取得すると、空を返す")
        void returnsEmptyForUnknownOrder() {
            // 操作と確認：ない注文 ID には空
            assertThat(repository.lockById(UUID.randomUUID())).isEmpty();
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @DisplayName("注文を行ロックして取得している間は、別のトランザクションが同じ注文の行をロックできない")
        void blocksOtherTransactionWhileLocked() {
            // 準備：ロールバックしないので、このテストで作った注文の行だけを使う
            var order = order(UUID.randomUUID(), orderNumber());
            transactionTemplate.executeWithoutResult(status -> repository.insert(order));
            var executor = Executors.newSingleThreadExecutor();
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    // 操作：行ロックしたまま、別のトランザクションで同じ行を待たずにロックする
                    assertThat(repository.lockById(order.id())).isPresent();
                    var other = executor.submit(() -> transactionTemplate.executeWithoutResult(inner ->
                            dsl.selectFrom(SALES_ORDER).where(SALES_ORDER.ID.eq(order.id())).forUpdate().noWait().fetch()));

                    // 確認：ロックを取れずに失敗する
                    assertThatThrownBy(() -> other.get(AsyncEvents.TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                            .isInstanceOf(ExecutionException.class)
                            .hasMessageContaining("could not obtain lock");
                });
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("会員のロック")
    class MemberLock {

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @DisplayName("AC-406-14 会員のロックを取っている間は、別のトランザクションが同じ会員のロックを取れず、別の会員のロックは取れる")
        void blocksOtherTransactionWhileMemberLocked() {
            // 準備：ロックはトランザクションの終わりで外れ、行を作らないので、ロールバックは必要ない
            var memberId = UUID.randomUUID();
            var executor = Executors.newSingleThreadExecutor();
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    // 操作：会員のロックを取る
                    repository.lockForMember(memberId);

                    // 確認：別のトランザクションは、同じ会員のロックを取れず、別の会員のロックは取れる
                    assertThat(tryLockInOtherTransaction(executor, memberId)).isFalse();
                    assertThat(tryLockInOtherTransaction(executor, UUID.randomUUID())).isTrue();
                });

                // 確認：トランザクションが終わると、同じ会員のロックを取れる
                assertThat(tryLockInOtherTransaction(executor, memberId)).isTrue();
            } finally {
                executor.shutdownNow();
            }
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 受付に変えて更新した注文が、状態とバージョンだけ変わっていることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>状態が受付</li>
     *   <li>バージョンが 1（保存の後の 1 回の更新）</li>
     *   <li>成立の時点の項目（{@link #assertStandardContent}）</li>
     *   <li>キャンセルの申請とキャンセルがない</li>
     * </ul>
     *
     * @param restored    読み直した注文の写し
     * @param orderId     注文 ID（実行ごとに作るので、定数にできない。以下も同じ）
     * @param checkoutId  注文手続き ID
     * @param orderNumber 注文番号
     */
    private static void assertAcceptedUpdate(Order.Snapshot restored, UUID orderId, UUID checkoutId, String orderNumber) {
        assertThat(restored.status()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(restored.version()).isEqualTo(1);
        assertStandardContent(restored, orderId, checkoutId, orderNumber);
        assertThat(restored.cancellationRequest()).isNull();
        assertThat(restored.cancellation()).isNull();
    }

    /**
     * 標準の注文（{@link #order}）を保存したまま、変えていないことを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>状態が支払待ちで、バージョンが 0</li>
     *   <li>成立の時点の項目（{@link #assertStandardContent}）</li>
     *   <li>キャンセルの申請とキャンセルがない</li>
     * </ul>
     *
     * @param restored    読み直した注文の写し
     * @param orderId     注文 ID（実行ごとに作るので、定数にできない。以下も同じ）
     * @param checkoutId  注文手続き ID
     * @param orderNumber 注文番号
     */
    private static void assertPlacedStandardOrder(Order.Snapshot restored, UUID orderId, UUID checkoutId, String orderNumber) {
        assertThat(restored.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(restored.version()).isZero();
        assertStandardContent(restored, orderId, checkoutId, orderNumber);
        assertThat(restored.cancellationRequest()).isNull();
        assertThat(restored.cancellation()).isNull();
    }

    /**
     * 標準の注文（{@link #order}）の、成立の時点の項目を確認する。
     *
     * <p>確認すること（期待値は定数）：
     * <ul>
     *   <li>注文 ID、注文番号、注文手続き ID、会員 ID、注文日時</li>
     *   <li>明細の全ての項目と並び（{@link #LINES}）</li>
     *   <li>金額の内訳（{@link #AMOUNTS}）</li>
     *   <li>送料の消費税（{@link #SHIPPING_TAX}）</li>
     *   <li>メールアドレス、お届け先、注文時の年齢と性別</li>
     * </ul>
     *
     * @param restored    読み直した注文の写し
     * @param orderId     注文 ID（実行ごとに作るので、定数にできない。以下も同じ）
     * @param checkoutId  注文手続き ID
     * @param orderNumber 注文番号
     */
    private static void assertStandardContent(Order.Snapshot restored, UUID orderId, UUID checkoutId, String orderNumber) {
        assertOrderIdentity(restored, orderId, checkoutId, orderNumber);
        assertThat(restored.lines()).containsExactlyElementsOf(LINES);
        assertThat(restored.amounts()).isEqualTo(AMOUNTS);
        assertThat(restored.shippingTax()).isEqualTo(SHIPPING_TAX);
        assertThat(restored.email()).isEqualTo(EMAIL);
        assertThat(restored.shippingAddress()).isEqualTo(ADDRESS);
        assertThat(restored.buyerProfile()).isEqualTo(BUYER_PROFILE);
    }

    /**
     * 上限の注文（{@link #maxOrder}）の、成立の時点の項目を確認する。
     *
     * <p>確認すること（期待値は定数）：
     * <ul>
     *   <li>注文 ID、注文番号、注文手続き ID、会員 ID、注文日時</li>
     *   <li>明細（{@link #MAX_LINE} の 1 行）</li>
     *   <li>金額の内訳（{@link #MAX_AMOUNTS}）</li>
     *   <li>送料の消費税（{@link #SHIPPING_TAX}）</li>
     *   <li>254 文字のメールアドレス、上限の長さのお届け先、120 歳の年齢と性別</li>
     * </ul>
     *
     * @param restored    読み直した注文の写し
     * @param orderId     注文 ID（実行ごとに作るので、定数にできない。以下も同じ）
     * @param checkoutId  注文手続き ID
     * @param orderNumber 注文番号
     */
    private static void assertMaxContent(Order.Snapshot restored, UUID orderId, UUID checkoutId, String orderNumber) {
        assertOrderIdentity(restored, orderId, checkoutId, orderNumber);
        assertThat(restored.lines()).containsExactly(MAX_LINE);
        assertThat(restored.amounts()).isEqualTo(MAX_AMOUNTS);
        assertThat(restored.shippingTax()).isEqualTo(SHIPPING_TAX);
        assertThat(restored.email()).isEqualTo(MAX_EMAIL);
        assertThat(restored.shippingAddress()).isEqualTo(MAX_ADDRESS);
        assertThat(restored.buyerProfile()).isEqualTo(MAX_BUYER_PROFILE);
    }

    /**
     * 注文の識別の項目と注文日時を確認する。
     *
     * @param restored    読み直した注文の写し
     * @param orderId     注文 ID（実行ごとに作るので、定数にできない。以下も同じ）
     * @param checkoutId  注文手続き ID
     * @param orderNumber 注文番号
     */
    private static void assertOrderIdentity(Order.Snapshot restored, UUID orderId, UUID checkoutId, String orderNumber) {
        assertThat(restored.id()).isEqualTo(orderId);
        assertThat(restored.orderNumber()).isEqualTo(OrderNumber.of(orderNumber));
        assertThat(restored.checkoutId()).isEqualTo(checkoutId);
        assertThat(restored.memberId()).isEqualTo(MEMBER_ID);
        assertThat(restored.orderedAt()).isEqualTo(ORDERED_AT);
    }

    /**
     * 申請の承認でキャンセルした注文の写しを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>状態がキャンセル</li>
     *   <li>申請が、申請の日時と理由、承認の結果、承認の日時、承認した運営者を持つ</li>
     *   <li>キャンセルが、承認の日時、申請の承認の種類、運営者を持つ（理由はない）</li>
     *   <li>バージョンが 3（保存の後の、受付・申請・承認の 3 回の更新）</li>
     * </ul>
     *
     * @param restored 読み直した注文の写し
     * @param operator 承認した運営者（ID を実行ごとに作るので、定数にできない）
     */
    private static void assertApprovedCancellation(Order.Snapshot restored, Operator operator) {
        assertThat(restored.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(restored.cancellationRequest()).isEqualTo(new CancellationRequest(REQUESTED_AT, REQUEST_REASON,
                CancellationRequest.Result.APPROVED, DECIDED_AT, operator));
        assertThat(restored.cancellation()).isEqualTo(
                new Cancellation(DECIDED_AT, CancellationKind.REQUEST_APPROVED, null, operator));
        assertThat(restored.version()).isEqualTo(3);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 注文を保存し、受付にして更新する。
     *
     * @return 注文 ID
     */
    private UUID acceptedOrder() {
        var order = order(UUID.randomUUID(), orderNumber());
        repository.insert(order);
        var accepted = repository.findById(order.id()).orElseThrow();
        accepted.acceptPayment(ACCEPTED_AT);
        repository.update(accepted);
        return order.id();
    }

    /**
     * 別のトランザクションで、会員のロックを待たずに取れるかを返す。
     *
     * @param executor 別のトランザクションを動かすスレッド
     * @param memberId 会員 ID
     * @return 取れたら true
     */
    private boolean tryLockInOtherTransaction(ExecutorService executor, UUID memberId) {
        try {
            return executor.submit(() -> transactionTemplate.execute(inner ->
                            dsl.fetchValue("select pg_try_advisory_xact_lock(?, ?)",
                                    AdvisoryLockSpace.ORDER.key(), AdvisoryLocks.hash(memberId))))
                    .get(AsyncEvents.TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                    .equals(true);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 他のテストが使わない日付と、乱数の 6 桁で作った注文番号を返す。
     *
     * @return 注文番号
     */
    private static String orderNumber() {
        return ORDER_NUMBER_DATE + "-" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    /**
     * 指定の注文番号とは別の注文番号を返す。
     *
     * @param orderNumber 注文番号
     * @return 別の注文番号
     */
    private static String otherThan(String orderNumber) {
        var next = (Integer.parseInt(orderNumber.substring(ORDER_NUMBER_DATE.length() + 1)) + 1) % 1_000_000;
        return ORDER_NUMBER_DATE + "-" + String.format("%06d", next);
    }

    /**
     * 2 つの税率の品目を持つ注文を作る。
     *
     * @param checkoutId  注文手続き ID
     * @param orderNumber 注文番号
     * @return 注文（支払待ち）
     */
    private static Order order(UUID checkoutId, String orderNumber) {
        var shirt = new VariantInfo(SHIRT_VARIANT_ID, UUID.randomUUID(), SHIRT_PRODUCT_CODE, SHIRT_VARIANT_CODE, SHIRT_NAME,
                SHIRT_AXES, SHIRT_PRICE_EXCL_TAX, UUID.randomUUID(), null, true, false);
        var tea = new VariantInfo(TEA_VARIANT_ID, UUID.randomUUID(), TEA_CODE, TEA_CODE, TEA_NAME, List.of(),
                TEA_PRICE_EXCL_TAX, UUID.randomUUID(), null, true, false);
        var items = List.of(
                QuoteItem.of(shirt, 1, 10, STANDARD_RATE, SHIRT_PRICE_INCL_TAX),
                QuoteItem.of(tea, 1, 10, REDUCED_RATE, TEA_PRICE_INCL_TAX));
        return Order.place(UUID.randomUUID(), OrderNumber.of(orderNumber), checkoutId, MEMBER_ID, ORDERED_AT,
                new Quote(items, AMOUNTS, SHIPPING_TAX, true), EMAIL, ADDRESS, BUYER_PROFILE);
    }

    /**
     * 各項目が domain の上限の注文を作る。
     *
     * <p>値は {@link #MAX_LINE}・{@link #MAX_AMOUNTS}・{@link #MAX_EMAIL}・{@link #MAX_ADDRESS}・{@link #MAX_BUYER_PROFILE}。
     *
     * @param checkoutId  注文手続き ID
     * @param orderNumber 注文番号
     * @return 注文（支払待ち）
     */
    private static Order maxOrder(UUID checkoutId, String orderNumber) {
        var variant = new VariantInfo(SHIRT_VARIANT_ID, UUID.randomUUID(), MAX_CODE, MAX_CODE, MAX_NAME, List.of(),
                MAX_PRICE_EXCL_TAX, UUID.randomUUID(), null, true, false);
        var item = QuoteItem.of(variant, MAX_QUANTITY, MAX_QUANTITY, STANDARD_RATE, MAX_PRICE_INCL_TAX);
        return Order.place(UUID.randomUUID(), OrderNumber.of(orderNumber), checkoutId, MEMBER_ID, ORDERED_AT,
                new Quote(List.of(item), MAX_AMOUNTS, SHIPPING_TAX, true), MAX_EMAIL, MAX_ADDRESS, MAX_BUYER_PROFILE);
    }
}
