package com.ecsite.module.order.infrastructure.persistence;

import com.ecsite.IntegrationTest;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 既存の注文のお届け先を、都道府県と市区町村のコードに移すマイグレーションの受け入れテスト。<br />
 * テストごとに別のデータベースを作り、移行の前のバージョンまで流してから、今の形の注文を入れ、残りを流す。
 */
@IntegrationTest
class ShippingAreaMigrationTest {

    /**
     * 移行の前のバージョン（地域の最初のデータ）
     */
    private static final String BEFORE_MIGRATION = "202610200910";

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
    @DisplayName("AC-261 都道府県名をコードにし、住所の先頭の市区町村名を市区町村として住所から分ける（政令指定都市の区、郡を含む町村を含む）")
    void splitsMunicipality() {
        insertOrder("20261001-000001", "東京都", "千代田区千代田 1-1");
        insertOrder("20261001-000002", "神奈川県", "横浜市中区山下町 1");
        insertOrder("20261001-000003", "東京都", "西多摩郡瑞穂町箱根ケ崎 2335");

        database.flyway().migrate();

        assertThat(shippingArea("20261001-000001")).containsExactly(
                "13", "東京都", "13101", "千代田区", "千代田 1-1");
        assertThat(shippingArea("20261001-000002")).containsExactly(
                "14", "神奈川県", "14104", "横浜市中区", "山下町 1");
        assertThat(shippingArea("20261001-000003")).containsExactly(
                "13", "東京都", "13303", "西多摩郡瑞穂町", "箱根ケ崎 2335");
    }

    @Test
    @DisplayName("AC-261 市区町村と照合できない注文があれば、その注文番号を示して移行が失敗する")
    void failsWhenNotMatched() {
        insertOrder("20261001-000001", "東京都", "千代田区千代田 1-1");
        insertOrder("20261001-000009", "東京都", "横浜市中区山下町 1");

        assertThatThrownBy(() -> database.flyway().migrate())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("20261001-000009");
    }

    /**
     * 移行の前の形（都道府県は名前、住所は市区町村から）で、受付の注文を入れる
     */
    private void insertOrder(String orderNumber, String prefecture, String address) {
        jdbc.update("""
                        INSERT INTO ordering.sales_order (id, order_number, checkout_id, ordered_at, status,
                                items_total, shipping_fee, total_amount, email, recipient_name, postal_code, prefecture, address, phone,
                                version, created_at, updated_at, member_id)
                        VALUES (?, ?, ?, now(), 'ACCEPTED', 1000, 1000, 2000, 'taro@example.com', '山田 太郎', '1000001', ?, ?,
                                '0312345678', 0, now(), now(), ?)""",
                UUID.randomUUID(), orderNumber, UUID.randomUUID(), prefecture, address, UUID.randomUUID());
    }

    /**
     * 移行の後の、注文のお届け先の都道府県・市区町村・住所
     */
    private Object[] shippingArea(String orderNumber) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT prefecture_code, prefecture_name, municipality_code, municipality_name, address
                FROM ordering.sales_order WHERE order_number = ?""", orderNumber);
        return new Object[]{row.get("prefecture_code"), row.get("prefecture_name"), row.get("municipality_code"),
                row.get("municipality_name"), row.get("address")};
    }
}
