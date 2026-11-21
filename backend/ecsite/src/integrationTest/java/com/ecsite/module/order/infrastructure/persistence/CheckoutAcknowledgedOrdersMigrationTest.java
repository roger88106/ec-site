package com.ecsite.module.order.infrastructure.persistence;

import com.ecsite.support.config.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注文手続きに確認した注文の列を追加するマイグレーションの受け入れテスト
 *
 * <p>適用の時点の支払待ちの注文と、進行中の注文手続きの扱いを確認する。適用の後の開始と注文の判定は、
 * {@code CheckoutPendingOrderConfirmationApiTest}・{@code PlaceOrderPendingConfirmationApiTest} が確認する。
 *
 * <p>テストごとに別のデータベースを作り、マイグレーションの前のバージョンまで流してから、適用の時点のデータを入れ、残りを流す。
 */
@IntegrationTest
class CheckoutAcknowledgedOrdersMigrationTest {

    // ============================== 定数 ==============================

    /**
     * マイグレーションの前のバージョン（V202611062100 の 1 つ前）
     */
    private static final String BEFORE_MIGRATION = "202610211600";

    // ============================== Bean ==============================

    /**
     * 統合テストの PostgreSQL
     */
    @Autowired
    PostgreSQLContainer postgres;

    /**
     * テストのデータベース
     */
    private MigrationDatabase database;

    /**
     * テストのデータベースの SQL
     */
    private JdbcTemplate jdbc;

    @BeforeEach
    void createDatabase() throws SQLException {
        database = MigrationDatabase.migratedTo(postgres, BEFORE_MIGRATION);
        jdbc = database.jdbc();
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("NFR-406-2 同じ会員の支払待ちの注文が複数あっても適用が成功し、注文は変わらない")
    void keepsPendingOrders() {
        // 準備：同じ会員の支払待ちの注文 2 件と、進行中の注文手続き
        var memberId = UUID.randomUUID();
        insertPendingOrdersAndCheckout(memberId);
        var ordersBefore = orders(memberId);

        // 確認（前提）：注文が 2 件ある
        assertThat(ordersBefore).hasSize(2);

        // 操作：残りのマイグレーションを流す
        var result = database.flyway().migrate();

        // 確認：適用が成功し、注文の適用の前にあった全ての列が適用の前と同じで、後のマイグレーションで追加した送料の消費税の列は null のまま（既存の注文を埋めない）
        assertThat(result.success).isTrue();
        var ordersAfter = orders(memberId);
        assertThat(ordersAfter).hasSameSizeAs(ordersBefore);
        for (var i = 0; i < ordersBefore.size(); i++) {
            assertThat(ordersAfter.get(i)).containsAllEntriesOf(ordersBefore.get(i));
            assertThat(ordersAfter.get(i)).containsEntry("shipping_tax", null);
        }
    }

    @Test
    @DisplayName("AC-406-18 支払待ちの注文がある会員の進行中の注文手続きは、確認した注文のない状態（空の配列）になる")
    void startsCheckoutsWithoutAcknowledgement() {
        // 準備：同じ会員の支払待ちの注文 2 件と、進行中の注文手続き
        var checkoutId = insertPendingOrdersAndCheckout(UUID.randomUUID());

        // 操作：残りのマイグレーションを流す
        database.flyway().migrate();

        // 確認：確認した注文が空の配列（注文するときに、確認を求められる）
        assertThat(jdbc.queryForObject("SELECT acknowledged_order_ids::text FROM ordering.checkout WHERE id = ?",
                String.class, checkoutId)).isEqualTo("[]");
    }

    // ============================== 準備の補助 ==============================

    /**
     * マイグレーションの前の形で、会員の支払待ちの注文 2 件と、進行中の注文手続きを入れる。
     *
     * @param memberId 会員 ID
     * @return 注文手続き ID
     */
    private UUID insertPendingOrdersAndCheckout(UUID memberId) {
        insertPendingOrder("20261101-000001", memberId);
        insertPendingOrder("20261101-000002", memberId);
        return insertCheckout(memberId);
    }

    /**
     * マイグレーションの前の形で、支払待ちの注文を入れる。
     *
     * @param orderNumber 注文番号
     * @param memberId    会員 ID
     */
    private void insertPendingOrder(String orderNumber, UUID memberId) {
        jdbc.update("""
                        INSERT INTO ordering.sales_order (id, order_number, checkout_id, ordered_at, status,
                                items_total, shipping_fee, total_amount, email, recipient_name, postal_code,
                                prefecture_code, prefecture_name, municipality_code, municipality_name, address, phone,
                                version, created_at, updated_at, member_id)
                        VALUES (?, ?, ?, now(), 'PENDING_PAYMENT', 1000, 1000, 2000, 'taro@example.com', '山田 太郎', '1000001',
                                '13', '東京都', '13101', '千代田区', '千代田 1-1', '0312345678', 0, now(), now(), ?)""",
                UUID.randomUUID(), orderNumber, UUID.randomUUID(), memberId);
    }

    /**
     * マイグレーションの前の形で、進行中の注文手続きを入れ、注文手続き ID を返す。
     *
     * @param memberId 会員 ID
     * @return 注文手続き ID
     */
    private UUID insertCheckout(UUID memberId) {
        var checkoutId = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO ordering.checkout (id, cart_id, cart_version, expires_at, created_at, member_id)
                        VALUES (?, ?, 1, now() + interval '15 minutes', now(), ?)""",
                checkoutId, UUID.randomUUID(), memberId);
        return checkoutId;
    }

    /**
     * 会員の注文の全ての列を、注文番号の順に返す。
     *
     * @param memberId 会員 ID
     * @return 注文の行
     */
    private List<Map<String, Object>> orders(UUID memberId) {
        return jdbc.queryForList("SELECT * FROM ordering.sales_order WHERE member_id = ? ORDER BY order_number", memberId);
    }
}
