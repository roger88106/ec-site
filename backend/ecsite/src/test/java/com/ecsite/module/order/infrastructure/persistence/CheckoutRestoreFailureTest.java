package com.ecsite.module.order.infrastructure.persistence;

import com.ecsite.jooq.ordering.tables.records.CheckoutRecord;
import com.ecsite.support.RestoreFailures;
import org.jooq.JSONB;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.ecsite.jooq.ordering.Tables.CHECKOUT;
import static com.ecsite.support.RestoreFailures.RECORDS;
import static com.ecsite.support.RestoreFailures.assertValueIsHidden;

/**
 * 注文手続きを DB の値から戻すときの失敗の単体テスト
 *
 * <p>DB の値を戻せないとき（読めない JSON、不変条件の違反）に、復元の失敗になり、例外の出力に DB の値が出ないことだけを確認する。
 * 値を正しく戻すことは、Repository の統合テストが確認する。
 */
class CheckoutRestoreFailureTest {

    // ============================== 定数 ==============================

    /**
     * 作成日時
     *
     * <p>例外の出力に入ると分かるよう、マイクロ秒まで持つ。
     */
    private static final Instant CREATED_AT = Instant.parse("2026-11-01T00:00:00.123456Z");

    // ============================== テスト ==============================

    @Test
    @DisplayName("確認した注文の ID を読めないとき、復元の失敗になり、例外の出力に値が出ない")
    void hidesAcknowledgedOrderIds() {
        // 準備：確認した注文の ID が UUID でない行
        var value = RestoreFailures.value();
        var checkout = validCheckout();
        checkout.setAcknowledgedOrderIds(JSONB.valueOf("[\"" + value + "\"]"));

        // 操作と確認：例外の出力に値が出ない
        assertValueIsHidden(() -> repository(checkout).findById(UUID.randomUUID()), value);
    }

    @Test
    @DisplayName("引当の期限が作成日時より前のとき、復元の失敗になり、例外の出力に日時と会員 ID が出ない")
    void hidesExpiryBeforeCreation() {
        // 準備：引当の期限が作成日時の 1 マイクロ秒前の行
        var expiresAt = CREATED_AT.minus(1, ChronoUnit.MICROS);
        var checkout = validCheckout();
        checkout.setExpiresAt(expiresAt);

        // 操作と確認：例外の出力に、期限・作成日時・会員 ID が出ない
        assertValueIsHidden(() -> repository(checkout).findById(UUID.randomUUID()), expiresAt.toString());
        assertValueIsHidden(() -> repository(checkout).findById(UUID.randomUUID()), CREATED_AT.toString());
        assertValueIsHidden(() -> repository(checkout).findById(UUID.randomUUID()), checkout.getMemberId().toString());
    }

    @Test
    @DisplayName("引当の期限が作成日時と同じとき、復元の失敗になり、例外の出力に日時が出ない")
    void hidesExpiryAtCreation() {
        // 準備：引当の期限が作成日時と同じ行
        var checkout = validCheckout();
        checkout.setExpiresAt(CREATED_AT);

        // 操作と確認：例外の出力に日時が出ない
        assertValueIsHidden(() -> repository(checkout).findById(UUID.randomUUID()), CREATED_AT.toString());
    }

    // ============================== 準備の補助 ==============================

    /**
     * 指定の行を返す、注文手続きのリポジトリを作る。
     *
     * @param checkout 返す行
     * @return リポジトリ
     */
    private static JooqCheckoutRepository repository(CheckoutRecord checkout) {
        return new JooqCheckoutRepository(new RestoreFailures().with("checkout", checkout).dsl(), JsonMapper.builder().build());
    }

    /**
     * 戻せる注文手続きの行を作る。
     *
     * <p>引当の期限は作成日時の 15 分後。
     *
     * @return 注文手続きの行
     */
    private static CheckoutRecord validCheckout() {
        var checkout = RECORDS.newRecord(CHECKOUT);
        checkout.setId(UUID.randomUUID());
        checkout.setMemberId(UUID.randomUUID());
        checkout.setCartId(UUID.randomUUID());
        checkout.setCartVersion(1L);
        checkout.setExpiresAt(CREATED_AT.plus(15, ChronoUnit.MINUTES));
        checkout.setCreatedAt(CREATED_AT);
        checkout.setAcknowledgedOrderIds(JSONB.valueOf("[]"));
        return checkout;
    }
}
