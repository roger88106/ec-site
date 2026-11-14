package com.ecsite.module.reporting.infrastructure.persistence;

import com.ecsite.AsyncEvents;
import com.ecsite.IntegrationTest;
import com.ecsite.common.model.value.Money;
import com.ecsite.common.persistence.AdvisoryLockSpace;
import com.ecsite.common.persistence.AdvisoryLocks;
import com.ecsite.module.reporting.application.port.ReportOrderRepository;
import com.ecsite.module.reporting.domain.snapshot.DeliveryStatus;
import com.ecsite.module.reporting.domain.snapshot.HistoryKind;
import com.ecsite.module.reporting.domain.snapshot.ReportBuyerGender;
import com.ecsite.module.reporting.domain.snapshot.ReportCancellationKind;
import com.ecsite.module.reporting.domain.snapshot.ReportOrder;
import com.ecsite.module.reporting.domain.snapshot.ReportOrderStatus;
import com.ecsite.module.reporting.domain.snapshot.StatusHistoryEntry;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * レポートの注文の写しと状態の履歴のリポジトリの統合テスト
 *
 * <p>ロックのテストを除き、テストごとにロールバックする。注文 ID はテストごとに作る。
 *
 * <p>準備のデータと確認の期待値は、同じ定数から作る。
 */
@IntegrationTest
@Transactional
class JooqReportOrderRepositoryTest {

    // ============================== 定数 ==============================

    /**
     * 注文日時
     *
     * <p>日本時間の 2026-10-08 00:30。
     */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-07T15:30:00Z");

    /**
     * 出荷日時
     *
     * <p>日本時間の 2026-10-09 23:30。
     */
    private static final Instant SHIPPED_AT = Instant.parse("2026-10-09T14:30:00Z");

    /**
     * 注文番号
     */
    private static final String ORDER_NUMBER = "20261008-000001";

    /**
     * お届け先の郵便番号
     */
    private static final String POSTAL_CODE = "1000001";

    /**
     * お届け先の都道府県のコード
     *
     * <p>東京都。
     */
    private static final String PREFECTURE_CODE = "13";

    /**
     * 注文時の年齢
     */
    private static final int BUYER_AGE = 36;

    /**
     * 総額
     */
    private static final long TOTAL_AMOUNT = 5302;

    /**
     * 送料
     */
    private static final long SHIPPING_FEE = 1000;

    /**
     * 消費税の合計
     */
    private static final long TAX_TOTAL = 458;

    /**
     * 送料の消費税
     */
    private static final long SHIPPING_TAX = 91;

    @Autowired
    ReportOrderRepository repository;

    /**
     * テストが直接 DB を読み書きする jOOQ の DSLContext
     */
    @Autowired
    DSLContext dsl;

    /**
     * 別のトランザクションで処理を実行する TransactionTemplate
     */
    @Autowired
    TransactionTemplate transactionTemplate;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("写しの保存")
    class Save {

        @Test
        @DisplayName("写しを保存すると、注文の項目を保存する")
        void savesOrderColumns() {
            // 準備：配送中の注文の写し
            var order = shippedOrder(UUID.randomUUID());

            // 操作：保存する
            repository.save(order);

            // 確認：注文の行の項目が、写しの値のまま
            assertOrderRowSaved(orderRow(order.orderId()), order.memberId());
        }

        @Test
        @DisplayName("写しを保存すると、注文日と出荷日は日本時間の日付になる")
        void savesJapanDates() {
            // 準備：日本時間では日付が変わる時刻に注文・出荷した写し
            var order = shippedOrder(UUID.randomUUID());

            // 操作：保存する
            repository.save(order);

            // 確認：日本時間の 2026-10-08 と 2026-10-09
            var row = orderRow(order.orderId());
            assertThat(row.get("ordered_on", LocalDate.class)).isEqualTo(LocalDate.of(2026, 10, 8));
            assertThat(row.get("shipped_on", LocalDate.class)).isEqualTo(LocalDate.of(2026, 10, 9));
        }

        @Test
        @DisplayName("写しを保存すると、明細を保存し、軸の値を JSON で持つ")
        void savesLines() {
            // 準備：明細が 2 行の写し
            var order = shippedOrder(UUID.randomUUID());

            // 操作：保存する
            repository.save(order);

            // 確認：明細が 2 行で、1 行目の軸の値が JSON
            assertThat(dsl.fetchValue("select count(*) from reporting.sales_order_line where order_id = ?", order.orderId()))
                    .isEqualTo(2L);
            assertThat(dsl.fetchValue("select axis_values::text from reporting.sales_order_line where order_id = ? and line_no = 1",
                    order.orderId())).isEqualTo("[{\"name\": \"色\", \"value\": \"白\"}]");
        }

        @Test
        @DisplayName("同じ注文の写しをもう一度保存すると、注文の項目を上書きし、注文の行は増えない")
        void overwritesOrder() {
            // 準備：受付の写しを保存した後の、キャンセルの写し
            var orderId = UUID.randomUUID();
            repository.save(order(orderId, ReportOrderStatus.ACCEPTED,
                    List.of(line(1, UUID.randomUUID()), line(2, UUID.randomUUID()), line(3, UUID.randomUUID()))));
            var cancelled = cancelledOrder(orderId, UUID.randomUUID());

            // 操作：キャンセルの写しを保存する
            repository.save(cancelled);

            // 確認：注文の行は 1 つで、状態とキャンセルの項目が上書きされている
            assertThat(dsl.fetchValue("select count(*) from reporting.sales_order where order_id = ?", orderId)).isEqualTo(1L);
            assertThat(dsl.fetchValue("select status from reporting.sales_order where order_id = ?", orderId))
                    .isEqualTo("CANCELLED");
            assertThat(dsl.fetchValue("select cancellation_kind from reporting.sales_order where order_id = ?", orderId))
                    .isEqualTo("OPERATOR");
            assertThat(dsl.fetchValue("select restocked from reporting.sales_order where order_id = ?", orderId))
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("同じ注文の写しをもう一度保存すると、明細を入れ替える")
        void replacesLines() {
            // 準備：明細が 3 行の写しを保存した後の、明細が 1 行の写し
            var orderId = UUID.randomUUID();
            repository.save(order(orderId, ReportOrderStatus.ACCEPTED,
                    List.of(line(1, UUID.randomUUID()), line(2, UUID.randomUUID()), line(3, UUID.randomUUID()))));
            var newProductId = UUID.randomUUID();
            var cancelled = cancelledOrder(orderId, newProductId);

            // 操作：明細が 1 行の写しを保存する
            repository.save(cancelled);

            // 確認：明細は新しい 1 行だけ
            assertThat(dsl.fetchValue("select count(*) from reporting.sales_order_line where order_id = ?", orderId))
                    .isEqualTo(1L);
            assertThat(repository.productIds(orderId)).isEqualTo(Map.of(1, newProductId));
        }
    }

    @Nested
    @DisplayName("明細の商品 ID")
    class ProductIds {

        @Test
        @DisplayName("明細の商品 ID を、行番号ごとに読め、商品 ID のない行を含めない")
        void readsProductIdsByLineNo() {
            // 準備：1 行目だけが商品 ID を持つ写し
            var productId = UUID.randomUUID();
            var order = order(UUID.randomUUID(), ReportOrderStatus.ACCEPTED, List.of(line(1, productId), line(2, null)));
            repository.save(order);

            // 操作と確認：1 行目の商品 ID だけ
            assertThat(repository.productIds(order.orderId())).isEqualTo(Map.of(1, productId));
        }

        @Test
        @DisplayName("どの行も商品 ID を持たなければ、空を返す")
        void emptyWithoutProductIds() {
            // 準備：どの行も商品 ID を持たない写し
            var order = order(UUID.randomUUID(), ReportOrderStatus.ACCEPTED, List.of(line(1, null), line(2, null)));
            repository.save(order);

            // 操作と確認：空
            assertThat(repository.productIds(order.orderId())).isEmpty();
        }

        @Test
        @DisplayName("写しのない注文には、空を返す")
        void emptyWithoutSnapshot() {
            // 操作と確認：保存していない注文 ID では空
            assertThat(repository.productIds(UUID.randomUUID())).isEmpty();
        }
    }

    @Nested
    @DisplayName("状態の履歴")
    class History {

        @Test
        @DisplayName("状態の履歴を追加すると、true を返す")
        void appendReturnsTrue() {
            // 準備：注文の状態の変化
            var entry = new StatusHistoryEntry(UUID.randomUUID(), UUID.randomUUID(), HistoryKind.ORDER_STATUS,
                    ReportOrderStatus.PENDING_PAYMENT, ReportOrderStatus.ACCEPTED, ORDERED_AT, null, null);

            // 操作と確認：追加できる
            assertThat(repository.appendHistory(entry)).isTrue();
        }

        @Test
        @DisplayName("注文の状態と配送状態の履歴を、発生日時の順に、キャンセルの種類と在庫に戻したかを含めて読める")
        void readsHistoryInOccurredOrder() {
            // 準備：発生日時と違う順の、キャンセル・受付・出荷済みの 3 行
            var orderId = UUID.randomUUID();
            var cancelled = new StatusHistoryEntry(UUID.randomUUID(), orderId, HistoryKind.ORDER_STATUS,
                    ReportOrderStatus.ACCEPTED, ReportOrderStatus.CANCELLED, ORDERED_AT.plusSeconds(120),
                    ReportCancellationKind.OPERATOR, true);
            var accepted = new StatusHistoryEntry(UUID.randomUUID(), orderId, HistoryKind.ORDER_STATUS,
                    ReportOrderStatus.PENDING_PAYMENT, ReportOrderStatus.ACCEPTED, ORDERED_AT.plusSeconds(60), null, null);
            var delivery = new StatusHistoryEntry(UUID.randomUUID(), orderId, HistoryKind.DELIVERY_STATUS, null,
                    DeliveryStatus.SHIPPED, ORDERED_AT.plusSeconds(90), null, null);

            // 確認（前提）：その順に追加すると、3 行とも追加できる
            assertThat(repository.appendHistory(cancelled)).isTrue();
            assertThat(repository.appendHistory(accepted)).isTrue();
            assertThat(repository.appendHistory(delivery)).isTrue();

            // 操作：履歴を読む
            var history = repository.history(orderId);

            // 確認：受付、出荷済み、キャンセルの順で、全ての項目が追加したもののまま
            assertThat(history).containsExactly(accepted, delivery, cancelled);
        }

        @Test
        @DisplayName("履歴のない注文には、空を返す")
        void emptyWithoutHistory() {
            // 準備：別の注文の履歴
            repository.appendHistory(new StatusHistoryEntry(UUID.randomUUID(), UUID.randomUUID(), HistoryKind.ORDER_STATUS,
                    ReportOrderStatus.PENDING_PAYMENT, ReportOrderStatus.ACCEPTED, ORDERED_AT, null, null));

            // 操作と確認：履歴を追加していない注文 ID では空
            assertThat(repository.history(UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("同じイベント ID の履歴を重ねて追加すると、追加せずに false を返す")
        void duplicateReturnsFalse() {
            // 準備：追加済みの履歴と、同じイベント ID の別の内容の履歴
            var orderId = UUID.randomUUID();
            var entry = acceptedEntry(orderId);
            repository.appendHistory(entry);
            var duplicate = duplicateOf(entry);

            // 操作と確認：追加しない
            assertThat(repository.appendHistory(duplicate)).isFalse();
        }

        @Test
        @DisplayName("同じイベント ID の履歴を重ねて追加しても、履歴は最初の 1 行のまま")
        void duplicateKeepsFirstRow() {
            // 準備：追加済みの履歴と、同じイベント ID の別の内容の履歴
            var orderId = UUID.randomUUID();
            var entry = acceptedEntry(orderId);
            repository.appendHistory(entry);
            var duplicate = duplicateOf(entry);

            // 操作：重ねて追加する
            repository.appendHistory(duplicate);

            // 確認：最初の 1 行だけ
            assertThat(repository.history(orderId)).containsExactly(entry);
        }
    }

    /**
     * 注文ごとのロック
     *
     * <p>ロックはトランザクションの終わりで外れ、行を作らないので、テストのトランザクションを使わない（ロールバックは要らない）。
     */
    @Nested
    @DisplayName("注文ごとのロック")
    class Lock {

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @DisplayName("注文ごとのロックを取っている間は、別のトランザクションが同じ注文のロックを取れない")
        void blocksSameOrder() {
            // 準備：別のトランザクションを動かすスレッド
            var orderId = UUID.randomUUID();
            var executor = Executors.newSingleThreadExecutor();
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    // 操作：注文のロックを取る
                    repository.lock(orderId);

                    // 確認：別のトランザクションは、同じ注文のロックを取れない
                    assertThat(tryLockInOtherTransaction(executor, orderId)).isFalse();
                });
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @DisplayName("注文ごとのロックを取っている間も、別のトランザクションは別の注文のロックを取れる")
        void allowsOtherOrder() {
            // 準備：別のトランザクションを動かすスレッド
            var executor = Executors.newSingleThreadExecutor();
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    // 操作：注文のロックを取る
                    repository.lock(UUID.randomUUID());

                    // 確認：別のトランザクションは、別の注文のロックを取れる
                    assertThat(tryLockInOtherTransaction(executor, UUID.randomUUID())).isTrue();
                });
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @DisplayName("ロックを取ったトランザクションが終わると、別のトランザクションが同じ注文のロックを取れる")
        void releasesAtTransactionEnd() {
            // 準備：別のトランザクションを動かすスレッド
            var orderId = UUID.randomUUID();
            var executor = Executors.newSingleThreadExecutor();
            try {
                // 操作：注文のロックを取ったトランザクションを終える
                transactionTemplate.executeWithoutResult(status -> repository.lock(orderId));

                // 確認：別のトランザクションは、同じ注文のロックを取れる
                assertThat(tryLockInOtherTransaction(executor, orderId)).isTrue();
            } finally {
                executor.shutdownNow();
            }
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 注文の行が、{@link #order} で作った写しの値のまま保存されていることを確認する。
     *
     * <p>確認すること（期待値は定数）：
     * <ul>
     *   <li>注文番号</li>
     *   <li>会員 ID</li>
     *   <li>状態が出荷済み、配送状態が配送中</li>
     *   <li>郵便番号、都道府県のコード</li>
     *   <li>注文時の年齢、性別</li>
     *   <li>総額、送料、消費税の合計、送料の消費税</li>
     * </ul>
     *
     * @param row      確認する注文の行
     * @param memberId 写しの会員 ID（実行ごとに作るので、定数にできない）
     */
    private static void assertOrderRowSaved(Record row, UUID memberId) {
        assertThat(row.get("order_number", String.class)).isEqualTo(ORDER_NUMBER);
        assertThat(row.get("member_id", UUID.class)).isEqualTo(memberId);
        assertThat(row.get("status", String.class)).isEqualTo("SHIPPED");
        assertThat(row.get("delivery_status", String.class)).isEqualTo("IN_TRANSIT");
        assertThat(row.get("postal_code", String.class)).isEqualTo(POSTAL_CODE);
        assertThat(row.get("prefecture_code", String.class)).isEqualTo(PREFECTURE_CODE);
        assertThat(row.get("buyer_age", Integer.class)).isEqualTo(BUYER_AGE);
        assertThat(row.get("buyer_gender", String.class)).isEqualTo("FEMALE");
        assertThat(row.get("total_amount", Long.class)).isEqualTo(TOTAL_AMOUNT);
        assertThat(row.get("shipping_fee", Long.class)).isEqualTo(SHIPPING_FEE);
        assertThat(row.get("tax_total", Long.class)).isEqualTo(TAX_TOTAL);
        assertThat(row.get("shipping_tax", Long.class)).isEqualTo(SHIPPING_TAX);
    }

    /**
     * 別のトランザクションで、注文のロックを待たずに取れるかを返す。
     *
     * @param executor 別のトランザクションを動かすスレッド
     * @param orderId  注文 ID
     * @return 取れたら true
     */
    private boolean tryLockInOtherTransaction(ExecutorService executor, UUID orderId) {
        try {
            return executor.submit(() -> transactionTemplate.execute(inner ->
                            dsl.fetchValue("select pg_try_advisory_xact_lock(?, ?)",
                                    AdvisoryLockSpace.REPORTING.key(), AdvisoryLocks.hash(orderId))))
                    .get(AsyncEvents.TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                    .equals(true);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 注文の行を読む。
     *
     * @param orderId 注文 ID
     * @return 注文の行
     */
    private Record orderRow(UUID orderId) {
        var row = dsl.fetchOne("select * from reporting.sales_order where order_id = ?", orderId);
        assertThat(row).isNotNull();
        return row;
    }

    /**
     * 出荷済み・配送中の注文の写しを作る。
     *
     * <p>明細は 2 行で、1 行目だけが商品 ID を持つ。
     *
     * @param orderId 注文 ID
     * @return 注文の写し
     */
    private static ReportOrder shippedOrder(UUID orderId) {
        return order(orderId, ReportOrderStatus.SHIPPED, List.of(line(1, UUID.randomUUID()), line(2, null)))
                .withShipment(SHIPPED_AT, DeliveryStatus.IN_TRANSIT);
    }

    /**
     * 担当者がキャンセルし、在庫に戻した注文の写しを作る。
     *
     * @param orderId   注文 ID
     * @param productId 1 行だけの明細の商品 ID
     * @return 注文の写し
     */
    private static ReportOrder cancelledOrder(UUID orderId, UUID productId) {
        return new ReportOrder(orderId, ORDER_NUMBER, UUID.randomUUID(), ORDERED_AT,
                ReportOrderStatus.CANCELLED, null, null, SHIPPED_AT, ReportCancellationKind.OPERATOR, true, POSTAL_CODE,
                PREFECTURE_CODE, BUYER_AGE, ReportBuyerGender.FEMALE, Money.of(TOTAL_AMOUNT), Money.of(SHIPPING_FEE),
                Money.of(TAX_TOTAL), Money.of(SHIPPING_TAX), List.of(line(1, productId)));
    }

    /**
     * 注文の写しを、定数から作る。
     *
     * <p>注文日時は、日本時間では翌日になる。
     *
     * @param orderId 注文 ID
     * @param status  状態
     * @param lines   明細
     * @return 注文の写し
     */
    private static ReportOrder order(UUID orderId, ReportOrderStatus status, List<ReportOrder.Line> lines) {
        return new ReportOrder(orderId, ORDER_NUMBER, UUID.randomUUID(), ORDERED_AT, status, null, null, null, null,
                null, POSTAL_CODE, PREFECTURE_CODE, BUYER_AGE, ReportBuyerGender.FEMALE, Money.of(TOTAL_AMOUNT),
                Money.of(SHIPPING_FEE), Money.of(TAX_TOTAL), Money.of(SHIPPING_TAX), lines);
    }

    /**
     * 指定した行番号と商品の注文の明細を作る。
     *
     * @param lineNo    行番号
     * @param productId 商品 ID。null なら商品 ID のない行
     * @return 明細
     */
    private static ReportOrder.Line line(int lineNo, UUID productId) {
        return new ReportOrder.Line(lineNo, productId, UUID.randomUUID(), "TS-001", "TS-001-WM", "T シャツ",
                List.of(new ReportOrder.AxisValue("色", "白")), 1, BigDecimal.TEN, Money.of(2970), Money.of(2700));
    }

    /**
     * 受付になった変化の履歴を作る。
     *
     * @param orderId 注文 ID
     * @return 履歴の 1 行
     */
    private static StatusHistoryEntry acceptedEntry(UUID orderId) {
        return new StatusHistoryEntry(UUID.randomUUID(), orderId, HistoryKind.ORDER_STATUS,
                ReportOrderStatus.PENDING_PAYMENT, ReportOrderStatus.ACCEPTED, ORDERED_AT, null, null);
    }

    /**
     * 同じイベント ID で、内容の違う履歴を作る。
     *
     * @param entry 元の履歴
     * @return 同じイベント ID の、期限切れのキャンセルの履歴
     */
    private static StatusHistoryEntry duplicateOf(StatusHistoryEntry entry) {
        return new StatusHistoryEntry(entry.eventId(), entry.orderId(), HistoryKind.ORDER_STATUS,
                ReportOrderStatus.ACCEPTED, ReportOrderStatus.CANCELLED, ORDERED_AT.plusSeconds(60),
                ReportCancellationKind.PAYMENT_EXPIRED, true);
    }
}
