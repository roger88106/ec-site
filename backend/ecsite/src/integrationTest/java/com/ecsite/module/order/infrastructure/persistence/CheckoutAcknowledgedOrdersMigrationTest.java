package com.ecsite.module.order.infrastructure.persistence;

import com.ecsite.IntegrationTest;
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
 * 注文手続きに確認した注文の列を追加するマイグレーションの受け入れテスト。<br />
 * テストごとに別のデータベースを作り、移行の前のバージョンまで流してから、適用の時点のデータを入れ、残りを流す。
 */
@IntegrationTest
class CheckoutAcknowledgedOrdersMigrationTest {

    /**
     * 移行の前のバージョン（V202611062100 の 1 つ前）
     */
    private static final String BEFORE_MIGRATION = "202610211600";

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

    @Test
    @DisplayName("AC-406-18、NFR-406-2 同じ会員の支払待ちの注文が複数あっても適用が成功し、注文は変わらず、進行中の注文手続きは確認した注文のない状態（空の配列）になる")
    void keepsPendingOrdersAndStartsCheckoutsWithoutAcknowledgement() {
        var memberId = UUID.randomUUID();
        insertPendingOrder("20261101-000001", memberId);
        insertPendingOrder("20261101-000002", memberId);
        var checkoutId = insertCheckout(memberId);
        var ordersBefore = orders(memberId);
        assertThat(ordersBefore).hasSize(2);

        var result = database.flyway().migrate();

        assertThat(result.success).isTrue();
        assertThat(orders(memberId)).isEqualTo(ordersBefore);
        assertThat(jdbc.queryForObject("SELECT acknowledged_order_ids::text FROM ordering.checkout WHERE id = ?",
                String.class, checkoutId)).isEqualTo("[]");
    }

    // ============================== 補助 ==============================

    /**
     * 移行の前の形で、支払待ちの注文を入れる
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
     * 移行の前の形で、進行中の注文手続きを入れ、注文手続き ID を返す
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
     * 会員の注文の全ての列
     */
    private List<Map<String, Object>> orders(UUID memberId) {
        return jdbc.queryForList("SELECT * FROM ordering.sales_order WHERE member_id = ? ORDER BY order_number", memberId);
    }
}
