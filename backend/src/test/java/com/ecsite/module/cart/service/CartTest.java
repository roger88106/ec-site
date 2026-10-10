package com.ecsite.module.cart.service;

import com.ecsite.common.error.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * カートの単体テスト（T2205、AC-74、75、81）
 */
class CartTest {

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    private static final UUID WHITE = UUID.randomUUID();

    private static final UUID BLACK = UUID.randomUUID();

    @Test
    @DisplayName("AC-73 作った直後のカートは空で、バージョンは 0")
    void createsEmptyCart() {
        var cart = Cart.create(UUID.randomUUID(), NOW);

        assertThat(cart.itemsAt(NOW)).isEmpty();
        assertThat(cart.version()).isZero();
    }

    @Test
    @DisplayName("AC-74 品目は入れた順に並び、同じバリアントを入れると数量を合算する（位置は変わらない）")
    void mergesSameVariant() {
        var cart = Cart.create(UUID.randomUUID(), NOW);

        cart.add(WHITE, 2, NOW);
        cart.add(BLACK, 1, NOW);
        cart.add(WHITE, 3, NOW);

        assertThat(cart.itemsAt(NOW)).extracting(CartItem::variantId, CartItem::quantity)
                .containsExactly(tuple(WHITE, 5), tuple(BLACK, 1));
    }

    @Test
    @DisplayName("AC-76 入れた後の数量を、追加の前に求められる（販売可能数と比べるため）")
    void calculatesQuantityAfterAdding() {
        var cart = Cart.create(UUID.randomUUID(), NOW);
        cart.add(WHITE, 2, NOW);

        assertThat(cart.quantityAfterAdding(WHITE, 3, NOW)).isEqualTo(5);
        assertThat(cart.quantityAfterAdding(BLACK, 3, NOW)).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 11})
    @DisplayName("AC-75 1 品目の数量は 1〜10。範囲外は拒否される")
    void rejectsQuantityOutOfRange(int quantity) {
        var cart = Cart.create(UUID.randomUUID(), NOW);

        assertBusinessError(() -> cart.add(WHITE, quantity, NOW), CartErrorCode.INVALID_CART_QUANTITY);
    }

    @Test
    @DisplayName("AC-75 合算して 10 を超える場合は拒否され、数量は変わらない")
    void rejectsMergedQuantityOver10() {
        var cart = Cart.create(UUID.randomUUID(), NOW);
        cart.add(WHITE, 6, NOW);

        assertBusinessError(() -> cart.add(WHITE, 5, NOW), CartErrorCode.INVALID_CART_QUANTITY);
        cart.add(WHITE, 4, NOW);

        assertThat(cart.itemsAt(NOW).getFirst().quantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("AC-75 30 品目まで入れられ、31 品目めは拒否される。既存の品目の合算はできる")
    void rejectsMoreThan30Items() {
        var cart = Cart.create(UUID.randomUUID(), NOW);
        var first = UUID.randomUUID();
        cart.add(first, 1, NOW);
        for (int i = 1; i < 30; i++) {
            cart.add(UUID.randomUUID(), 1, NOW);
        }

        assertBusinessError(() -> cart.add(UUID.randomUUID(), 1, NOW), CartErrorCode.TOO_MANY_CART_ITEMS);
        cart.add(first, 1, NOW);

        assertThat(cart.itemsAt(NOW)).hasSize(30);
    }

    @Test
    @DisplayName("AC-78 数量を変更・削除できる。カートにない品目の数量の変更は見つからない。削除は冪等")
    void changesAndRemovesItems() {
        var cart = Cart.create(UUID.randomUUID(), NOW);
        cart.add(WHITE, 2, NOW);
        cart.add(BLACK, 1, NOW);

        cart.changeQuantity(WHITE, 7, NOW);
        cart.remove(BLACK, NOW);
        cart.remove(BLACK, NOW);

        assertThat(cart.itemsAt(NOW)).extracting(CartItem::variantId, CartItem::quantity)
                .containsExactly(tuple(WHITE, 7));
        assertBusinessError(() -> cart.changeQuantity(BLACK, 1, NOW), CommonErrorCode.NOT_FOUND);
        assertBusinessError(() -> cart.changeQuantity(WHITE, 11, NOW), CartErrorCode.INVALID_CART_QUANTITY);
    }

    @Test
    @DisplayName("変更のたびにバージョンが上がる。何も変わらない削除では上がらない")
    void incrementsVersionOnChange() {
        var cart = Cart.create(UUID.randomUUID(), NOW);

        cart.add(WHITE, 1, NOW);
        cart.changeQuantity(WHITE, 2, NOW);
        cart.remove(WHITE, NOW);
        cart.remove(WHITE, NOW);

        assertThat(cart.version()).isEqualTo(3);
    }

    @Test
    @DisplayName("AC-81 最後の変更から 30 日たったカートは空として扱い、次に入れると古い品目は消える")
    void treatsExpiredCartAsEmpty() {
        var cart = Cart.create(UUID.randomUUID(), NOW);
        cart.add(WHITE, 2, NOW);
        var justBefore = NOW.plus(Duration.ofDays(30)).minusSeconds(1);
        var expired = NOW.plus(Duration.ofDays(30));

        assertThat(cart.itemsAt(justBefore)).hasSize(1);
        assertThat(cart.itemsAt(expired)).isEmpty();
        assertThat(cart.quantityAfterAdding(WHITE, 1, expired)).isEqualTo(1);

        cart.add(BLACK, 1, expired);

        assertThat(cart.itemsAt(expired)).extracting(CartItem::variantId).containsExactly(BLACK);
    }

    @Test
    @DisplayName("空にすると品目がなくなり、バージョンが上がる")
    void clearsItems() {
        var cart = Cart.create(UUID.randomUUID(), NOW);
        cart.add(WHITE, 2, NOW);

        cart.clear(NOW);

        assertThat(cart.itemsAt(NOW)).isEmpty();
        assertThat(cart.version()).isEqualTo(2);
    }
}
