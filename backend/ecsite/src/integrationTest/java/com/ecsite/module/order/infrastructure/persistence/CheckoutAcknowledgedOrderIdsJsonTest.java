package com.ecsite.module.order.infrastructure.persistence;

import com.ecsite.module.order.domain.checkout.Checkout;
import com.ecsite.module.order.domain.checkout.port.CheckoutRepository;
import com.ecsite.support.config.IntegrationTest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注文手続きの「確認した注文の ID」の JSON の列の形の統合テスト
 *
 * <p>注文の JSON の列のうち、識別子を含むのは、注文手続きの確認した注文の ID（{@code ordering.checkout.acknowledged_order_ids}）だけ。
 * 注文明細の軸の値（{@code axis_values}）とレポートの注文明細の軸の値は、名前と値の文字列だけを持つ。
 * 識別子の型に替える前後で、列の形が UUID の文字列の配列のままで、既に書いた列を読めることを確認する。
 *
 * <p>テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class CheckoutAcknowledgedOrderIdsJsonTest {

    // ============================== 定数 ==============================

    /**
     * 確認した注文の ID
     */
    private static final UUID ORDER_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    /**
     * 注文手続きの ID
     */
    private static final UUID CHECKOUT_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

    /**
     * 列の文字列の取得（jsonb を文字列にして読む）
     */
    private static final String SELECT_COLUMN = "SELECT acknowledged_order_ids::text FROM ordering.checkout WHERE id = ?";

    // ============================== Bean ==============================

    @Autowired
    CheckoutRepository repository;

    @Autowired
    DSLContext dsl;

    // ============================== テスト ==============================

    @Test
    @DisplayName("確認した注文の ID は、UUID の文字列の配列として列に書く")
    void writesOrderIdsAsUuidStringArray() {
        // 準備：確認した注文が 1 件の注文手続き
        var checkout = checkout(Set.of(ORDER_ID));

        // 操作：保存する
        repository.insert(checkout);

        // 確認：列は UUID の文字列の配列（jsonb の文字列の表現）
        assertThat(dsl.fetchValue(SELECT_COLUMN, CHECKOUT_ID)).isEqualTo("[\"33333333-3333-4333-8333-333333333333\"]");
    }

    @Test
    @DisplayName("確認した注文がないときは、空の配列を書く")
    void writesEmptyArray() {
        // 操作：確認した注文のない注文手続きを保存する
        repository.insert(checkout(Set.of()));

        // 確認：列は空の配列
        assertThat(dsl.fetchValue(SELECT_COLUMN, CHECKOUT_ID)).isEqualTo("[]");
    }

    @Test
    @DisplayName("UUID の文字列の配列の列から、確認した注文の ID を読める")
    void readsOrderIdsFromUuidStringArray() {
        // 準備：確認した注文のない注文手続きを保存し、列を、既に書いた形の JSON にする
        repository.insert(checkout(Set.of()));
        dsl.execute("UPDATE ordering.checkout SET acknowledged_order_ids = ?::jsonb WHERE id = ?",
                "[\"33333333-3333-4333-8333-333333333333\"]", CHECKOUT_ID);

        // 操作：読む
        var restored = repository.findById(CHECKOUT_ID).orElseThrow();

        // 確認：確認した注文の ID が復元される（その注文だけが確認済みで、ほかの注文は確認していない）
        assertThat(restored.acknowledges(Set.of(ORDER_ID))).isTrue();
        assertThat(restored.acknowledges(Set.of(ORDER_ID, UUID.randomUUID()))).isFalse();
    }

    // ============================== 準備の補助 ==============================

    /**
     * 注文手続きを作る。
     *
     * @param acknowledgedOrderIds 確認した注文の ID
     * @return 注文手続き
     */
    private static Checkout checkout(Set<UUID> acknowledgedOrderIds) {
        return Checkout.start(CHECKOUT_ID, UUID.randomUUID(), UUID.randomUUID(), 1, Instant.parse("2026-10-07T15:30:00Z"),
                Duration.ofMinutes(30), acknowledgedOrderIds);
    }
}
