package com.ecsite.module.order.infrastructure.persistence;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.jooq.ordering.tables.records.SalesOrderLineRecord;
import com.ecsite.jooq.ordering.tables.records.SalesOrderRecord;
import com.ecsite.module.order.application.port.OrderQueryRepository;
import com.ecsite.support.RestoreFailures;
import org.jooq.JSONB;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static com.ecsite.jooq.ordering.Tables.SALES_ORDER_LINE;
import static com.ecsite.support.RestoreFailures.RECORDS;
import static com.ecsite.support.RestoreFailures.assertDatabaseFailurePropagates;
import static com.ecsite.support.RestoreFailures.assertValueIsHidden;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 注文を DB の値から戻すときの失敗の単体テスト
 *
 * <p>DB の値を戻せないとき（列挙にない値、不変条件の違反）に、復元の失敗になり、例外の出力に DB の値が出ないことを確認する。
 * 壊した行が狙った条件で失敗したことは、原因の例外の種類と、壊していない行が戻せることで確認する。
 * 子の表を読む DB の失敗が、復元の失敗にならないことも確認する。値を正しく戻すことは、Repository の統合テストが確認する。
 */
class OrderRestoreFailureTest {

    // ============================== 定数 ==============================

    /**
     * 違反した金額
     *
     * <p>例外の出力に入ると分かるよう、他の値と重ならない桁の多い数にする。
     */
    private static final long VIOLATING_AMOUNT = 987_654_321_987L;

    // ============================== Bean ==============================

    /**
     * 例外のメッセージに入ると分かる値
     */
    private final String value = RestoreFailures.value();

    // ============================== テスト ==============================

    @Nested
    @DisplayName("注文を 1 件読むとき")
    class FindById {

        @Test
        @DisplayName("壊していない注文と明細の行は、戻せる")
        void restoresValidRows() {
            // 準備：戻せる注文と明細の行
            var database = new RestoreFailures().with("sales_order", validOrder()).with("sales_order_line", validLine());

            // 操作：注文を読む
            var order = repository(database).findById(UUID.randomUUID());

            // 確認：注文と明細を戻せる（他のテストの失敗が、準備の行のせいでないことの前提）
            assertThat(order).hasValueSatisfying(restored -> assertThat(restored.toSnapshot().lines()).hasSize(1));
        }

        @Test
        @DisplayName("注文の状態が列挙にないとき、例外の出力に値が出ない")
        void hidesStatus() {
            // 操作と確認：状態を列挙にない値にした行を読む
            assertFindHides(order -> order.setStatus(value), IllegalArgumentException.class);
        }

        @Test
        @DisplayName("注文時の性別が列挙にないとき、例外の出力に値が出ない")
        void hidesBuyerGender() {
            // 操作と確認：性別を列挙にない値にした行を読む
            assertFindHides(order -> order.setBuyerGender(value), IllegalArgumentException.class);
        }

        @Test
        @DisplayName("注文番号の形が違うとき、例外の出力に値が出ない")
        void hidesOrderNumber() {
            // 操作と確認：注文番号を形の違う値にした行を読む
            assertFindHides(order -> order.setOrderNumber(value), IllegalArgumentException.class);
        }

        @Test
        @DisplayName("キャンセルの申請の結果が列挙にないとき、例外の出力に値が出ない")
        void hidesCancellationRequestResult() {
            // 操作と確認：申請があり、その結果を列挙にない値にした行を読む
            assertFindHides(order -> {
                order.setCancellationRequestedAt(Instant.EPOCH);
                order.setCancellationRequestResult(value);
            }, IllegalArgumentException.class);
        }

        @Test
        @DisplayName("キャンセルの種類が列挙にないとき、例外の出力に値が出ない")
        void hidesCancellationKind() {
            // 操作と確認：キャンセルがあり、その種類を列挙にない値にした行を読む
            assertFindHides(order -> {
                order.setCancelledAt(Instant.EPOCH);
                order.setCancellationKind(value);
            }, IllegalArgumentException.class);
        }

        @Test
        @DisplayName("明細の軸の値を読めないとき、例外の出力に値が出ない")
        void hidesAxisValues() {
            // 準備：軸の値が名前と値の組でない明細の行
            var line = validLine();
            line.setAxisValues(JSONB.valueOf("[\"" + value + "\"]"));

            // 操作と確認：例外の出力に値が出ない
            assertLineHides(line, value, MismatchedInputException.class);
        }

        @Test
        @DisplayName("メールアドレスの形が違うとき、復元の失敗になり、例外の出力に値が出ない")
        void hidesInvalidEmail() {
            // 操作と確認：メールアドレスを @ のない値にした行を読む
            assertFindHides(order -> order.setEmail(value), BusinessException.class);
        }

        @Test
        @DisplayName("郵便番号の形が違うとき、復元の失敗になり、例外の出力に値が出ない")
        void hidesInvalidPostalCode() {
            // 操作と確認：郵便番号を数字 7 桁でない値にした行を読む
            assertFindHides(order -> order.setPostalCode(value), BusinessException.class);
        }

        @Test
        @DisplayName("電話番号の形が違うとき、復元の失敗になり、例外の出力に値が出ない")
        void hidesInvalidPhone() {
            // 操作と確認：電話番号を数字でない値にした行を読む
            assertFindHides(order -> order.setPhone(value), BusinessException.class);
        }

        @Test
        @DisplayName("お届け先の氏名が空白のとき、復元の失敗になり、例外の出力に値が出ない")
        void hidesBlankRecipientName() {
            // 操作と確認：氏名を空白にした行を読む。値は住所に入れる
            assertFindHides(order -> {
                order.setRecipientName(" ");
                order.setAddress(value);
            }, BusinessException.class);
        }

        @Test
        @DisplayName("お届け先の住所が空白のとき、復元の失敗になり、例外の出力に値が出ない")
        void hidesBlankAddress() {
            // 操作と確認：住所を空白にした行を読む。値は氏名に入れる
            assertFindHides(order -> {
                order.setAddress(" ");
                order.setRecipientName(value);
            }, BusinessException.class);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(shorts = {-1, 121})
        @DisplayName("注文時の年齢が範囲（0〜120）の外のとき、復元の失敗になり、例外の出力に値が出ない")
        void hidesBuyerAgeOutOfRange(short age) {
            // 操作と確認：年齢を範囲の 1 つ外にした行を読む。年齢は短い数で出力の他の数と区別できないので、値は氏名に入れる
            assertFindHides(order -> {
                order.setBuyerAge(age);
                order.setRecipientName(value);
            }, IllegalArgumentException.class);
        }

        @Test
        @DisplayName("却下した申請に決定の日時がないとき、復元の失敗になり、例外の出力に値が出ない")
        void hidesDecidedRequestWithoutDecidedAt() {
            // 操作と確認：受付の注文に、決定の日時のない却下の申請を持たせた行を読む。値は申請の理由に入れる
            assertFindHides(order -> {
                order.setCancellationRequestedAt(Instant.EPOCH);
                order.setCancellationRequestReason(value);
                order.setCancellationRequestResult("REJECTED");
            }, IllegalArgumentException.class);
        }

        @Test
        @DisplayName("申請中の申請に決定の日時があるとき、復元の失敗になり、例外の出力に値が出ない")
        void hidesPendingRequestWithDecidedAt() {
            // 操作と確認：申請中の注文に、決定の日時のある申請中の申請を持たせた行を読む。値は申請の理由に入れる
            assertFindHides(order -> {
                order.setStatus("CANCELLATION_REQUESTED");
                order.setCancellationRequestedAt(Instant.EPOCH);
                order.setCancellationRequestReason(value);
                order.setCancellationRequestResult("PENDING");
                order.setCancellationDecidedAt(Instant.EPOCH);
            }, IllegalArgumentException.class);
        }

        @Test
        @DisplayName("キャンセルの注文にキャンセルがないとき、復元の失敗になり、例外の出力に値が出ない")
        void hidesCancelledWithoutCancellation() {
            // 操作と確認：状態がキャンセルで、キャンセルの日時がない行を読む。値はメールアドレスに入れる
            assertFindHides(order -> {
                order.setStatus("CANCELLED");
                order.setEmail(value + "@example.com");
            }, IllegalArgumentException.class);
        }

        @Test
        @DisplayName("受付の注文の申請が申請中のとき、復元の失敗になり、例外の出力に値が出ない")
        void hidesAcceptedWithPendingRequest() {
            // 操作と確認：状態が受付で、申請中の申請を持つ行を読む。値は申請の理由に入れる
            assertFindHides(order -> {
                order.setCancellationRequestedAt(Instant.EPOCH);
                order.setCancellationRequestReason(value);
                order.setCancellationRequestResult("PENDING");
            }, IllegalArgumentException.class);
        }

        @Test
        @DisplayName("総額が商品の合計と送料の和と違うとき、復元の失敗になり、例外の出力に総額が出ない")
        void hidesInconsistentAmounts() {
            // 準備：総額だけを違反した金額にした行
            var order = validOrder();
            order.setTotalAmount(VIOLATING_AMOUNT);
            var database = new RestoreFailures().with("sales_order", order);

            // 操作と確認：例外の出力に総額が出ない
            assertValueIsHidden(() -> repository(database).findById(UUID.randomUUID()), Long.toString(VIOLATING_AMOUNT),
                    IllegalArgumentException.class);
        }

        @Test
        @DisplayName("明細の小計が税込単価と数量の積と違うとき、復元の失敗になり、例外の出力に小計が出ない")
        void hidesInconsistentLineSubtotal() {
            // 準備：小計だけを違反した金額にした明細の行
            var line = validLine();
            line.setSubtotal(VIOLATING_AMOUNT);

            // 操作と確認：例外の出力に小計が出ない
            assertLineHides(line, Long.toString(VIOLATING_AMOUNT), IllegalArgumentException.class);
        }

        @Test
        @DisplayName("明細の数量が 1 未満のとき、復元の失敗になり、例外の出力に値が出ない")
        void hidesLineQuantityBelowMinimum() {
            // 準備：数量と小計を 0 にした明細の行。値は商品名に入れる
            var line = validLine();
            line.setQuantity(0);
            line.setSubtotal(0L);
            line.setProductName(value);

            // 操作と確認：例外の出力に値が出ない
            assertLineHides(line, value, IllegalArgumentException.class);
        }

        @Test
        @DisplayName("明細の行番号が 1 未満のとき、復元の失敗になり、例外の出力に値が出ない")
        void hidesLineNoBelowMinimum() {
            // 準備：行番号を 0 にした明細の行。値は商品名に入れる
            var line = validLine();
            line.setLineNo((short) 0);
            line.setProductName(value);

            // 操作と確認：例外の出力に値が出ない
            assertLineHides(line, value, IllegalArgumentException.class);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"sales_order_line", "sales_order_tax"})
        @DisplayName("明細か税額の表を読む DB の失敗は、復元の失敗にならず、DB の失敗のまま出る")
        void propagatesChildTableFailure(String table) {
            // 準備：注文の行は戻せて、子の表を読むと失敗する DB
            var database = new RestoreFailures().with("sales_order", validOrder()).failingOn(table);

            // 操作と確認：DB の失敗のまま出る
            assertDatabaseFailurePropagates(() -> repository(database).findById(UUID.randomUUID()));
        }
    }

    @Nested
    @DisplayName("注文の一覧を読むとき")
    class FindOrders {

        @Test
        @DisplayName("注文の一覧で状態が列挙にないとき、例外の出力に値が出ない")
        void hidesStatusInList() {
            // 準備：状態が列挙にない一覧の行
            var row = RECORDS.newRecord(SALES_ORDER.ID, SALES_ORDER.ORDER_NUMBER, SALES_ORDER.ORDERED_AT, SALES_ORDER.RECIPIENT_NAME,
                    SALES_ORDER.TOTAL_AMOUNT, SALES_ORDER.STATUS).values(UUID.randomUUID(), "20261010-482915", Instant.EPOCH, "氏名", 0L, value);
            var dsl = new RestoreFailures().with("sales_order", row).dsl();
            var filter = new OrderQueryRepository.Filter(null, null, null, null, null, null);

            // 操作と確認：例外の出力に値が出ない
            assertValueIsHidden(() -> new JooqOrderQueryRepository(dsl).findOrders(filter, new PageRequest(1, 20)), value,
                    IllegalArgumentException.class);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 注文の行の一部を壊して注文を読み、狙った検査で復元の失敗になり、例外の出力に値が出ないことを確認する。
     *
     * @param breaking  行を壊す処理
     * @param causeType 狙った検査が投げる例外の種類
     */
    private void assertFindHides(Consumer<SalesOrderRecord> breaking, Class<? extends Exception> causeType) {
        var order = validOrder();
        breaking.accept(order);
        var database = new RestoreFailures().with("sales_order", order);

        assertValueIsHidden(() -> repository(database).findById(UUID.randomUUID()), value, causeType);
    }

    /**
     * 壊した明細の行を持つ注文を読み、狙った検査で復元の失敗になり、例外の出力に値が出ないことを確認する。
     *
     * @param line      壊した明細の行
     * @param hidden    出てはいけない値
     * @param causeType 狙った検査が投げる例外の種類
     */
    private static void assertLineHides(SalesOrderLineRecord line, String hidden, Class<? extends Exception> causeType) {
        var database = new RestoreFailures().with("sales_order", validOrder()).with("sales_order_line", line);

        assertValueIsHidden(() -> repository(database).findById(UUID.randomUUID()), hidden, causeType);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 注文のリポジトリを作る。
     *
     * @param database 行を返す mock
     * @return リポジトリ
     */
    private static JooqOrderRepository repository(RestoreFailures database) {
        return new JooqOrderRepository(database.dsl(), JsonMapper.builder().build(), mock(ApplicationEventPublisher.class),
                Clock.systemUTC());
    }

    /**
     * 戻せる注文の行を作る。
     *
     * @return 注文の行
     */
    private static SalesOrderRecord validOrder() {
        var order = RECORDS.newRecord(SALES_ORDER);
        order.setId(UUID.randomUUID());
        order.setOrderNumber("20261010-482915");
        order.setCheckoutId(UUID.randomUUID());
        order.setMemberId(UUID.randomUUID());
        order.setOrderedAt(Instant.EPOCH);
        order.setStatus("ACCEPTED");
        order.setItemsTotal(0L);
        order.setShippingFee(0L);
        order.setTotalAmount(0L);
        order.setEmail("taro@example.com");

        // お届け先
        order.setRecipientName("山田 太郎");
        order.setPostalCode("1000001");
        order.setPrefectureCode("13");
        order.setPrefectureName("東京都");
        order.setMunicipalityCode("13101");
        order.setMunicipalityName("千代田区");
        order.setAddress("千代田 1-1");
        order.setPhone("0312345678");

        order.setVersion(0L);
        return order;
    }

    /**
     * 戻せる明細の行を作る。
     *
     * <p>税込 1,100 円の商品を 2 個、小計 2,200 円。
     *
     * @return 明細の行
     */
    private static SalesOrderLineRecord validLine() {
        var line = RECORDS.newRecord(SALES_ORDER_LINE);
        line.setLineNo((short) 1);
        line.setVariantId(UUID.randomUUID());
        line.setProductCode("P-1");
        line.setVariantCode("P-1-1");
        line.setProductName("商品");
        line.setAxisValues(JSONB.valueOf("[]"));
        line.setUnitPriceExclTax(1000L);
        line.setRatePercent(BigDecimal.TEN);
        line.setUnitPriceInclTax(1100L);
        line.setQuantity(2);
        line.setSubtotal(2200L);
        return line;
    }
}
