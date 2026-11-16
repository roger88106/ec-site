package com.ecsite.module.cart.domain;

import com.ecsite.common.error.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * カートの単体テスト
 *
 * <p>品目の追加・変更・削除と上限、期限、バージョンだけを確認する。カートを移す処理は {@code CartMergeTest}、販売可能数と
 * 比べることと API を通した流れは、カートの API の統合テストが確認する。
 */
class CartTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * カートの期限（最後の変更からの時間）
     */
    private static final Duration EXPIRY = Duration.ofDays(30);

    /**
     * 白のバリアント ID
     */
    private static final UUID WHITE = UUID.randomUUID();

    /**
     * 黒のバリアント ID
     */
    private static final UUID BLACK = UUID.randomUUID();

    // ============================== テスト ==============================

    @Nested
    @DisplayName("カートの作成")
    class Creation {

        @Test
        @DisplayName("AC-73 作った直後のカートは空")
        void isEmpty() {
            // 操作：カートを作る
            var cart = newCart();

            // 確認：品目がない
            assertThat(cart.itemsAt(NOW)).isEmpty();
        }

        @Test
        @DisplayName("AC-73 作った直後のカートのバージョンは 0")
        void startsAtVersionZero() {
            // 操作：カートを作る
            var cart = newCart();

            // 確認：バージョンが 0
            assertThat(cart.version()).isZero();
        }
    }

    @Nested
    @DisplayName("品目の追加")
    class Add {

        @Test
        @DisplayName("AC-74 同じバリアントをもう一度入れると、元の位置のまま数量を合算する")
        void mergesSameVariant() {
            // 準備：白 2 個、黒 1 個の順に入れたカート
            var cart = newCart();
            cart.add(WHITE, 2, NOW);
            cart.add(BLACK, 1, NOW);

            // 操作：白を 3 個入れる
            cart.add(WHITE, 3, NOW);

            // 確認：白が先頭のまま 5 個
            assertThat(cart.itemsAt(NOW)).extracting(CartItem::variantId, CartItem::quantity)
                    .containsExactly(tuple(WHITE, 5), tuple(BLACK, 1));
        }

        @Test
        @DisplayName("AC-76 カートにあるバリアントは、入れた後の数量を合算した数で求められる")
        void calculatesQuantityAfterAddingExisting() {
            // 準備：白 2 個のカート
            var cart = newCart();
            cart.add(WHITE, 2, NOW);

            // 操作と確認：白を 3 個入れた後は 5 個（販売可能数と比べるため、追加の前に求める）
            assertThat(cart.quantityAfterAdding(WHITE, 3, NOW)).isEqualTo(5);
        }

        @Test
        @DisplayName("AC-76 カートにないバリアントは、入れた後の数量を入れる数で求められる")
        void calculatesQuantityAfterAddingNew() {
            // 準備：白 2 個のカート
            var cart = newCart();
            cart.add(WHITE, 2, NOW);

            // 操作と確認：黒を 3 個入れた後は 3 個
            assertThat(cart.quantityAfterAdding(BLACK, 3, NOW)).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("数量の上限")
    class QuantityLimit {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {0, -1, 11})
        @DisplayName("AC-75 1〜10 の外の数量は拒否される")
        void rejectsQuantityOutOfRange(int quantity) {
            // 準備：空のカート
            var cart = newCart();

            // 操作と確認：業務のエラー INVALID_CART_QUANTITY で拒否される
            assertBusinessError(() -> cart.add(WHITE, quantity, NOW), CartErrorCode.INVALID_CART_QUANTITY);
        }

        @Test
        @DisplayName("AC-75 合算して 10 を超える追加は拒否される")
        void rejectsMergedQuantityOverTen() {
            // 準備：白 6 個のカート
            var cart = newCart();
            cart.add(WHITE, 6, NOW);

            // 操作と確認：白を 5 個入れると、業務のエラー INVALID_CART_QUANTITY で拒否される
            assertBusinessError(() -> cart.add(WHITE, 5, NOW), CartErrorCode.INVALID_CART_QUANTITY);
        }

        @Test
        @DisplayName("AC-75 拒否された追加は、数量を変えない")
        void keepsQuantityAfterRejection() {
            // 準備：白 6 個のカートに、白 5 個を入れて拒否された
            var cart = newCart();
            cart.add(WHITE, 6, NOW);
            assertBusinessError(() -> cart.add(WHITE, 5, NOW), CartErrorCode.INVALID_CART_QUANTITY);

            // 確認：6 個のまま
            assertThat(cart.itemsAt(NOW).getFirst().quantity()).isEqualTo(6);
        }

        @Test
        @DisplayName("AC-75 合算してちょうど 10 になる追加は受け付ける")
        void acceptsMergedQuantityOfTen() {
            // 準備：白 6 個のカート
            var cart = newCart();
            cart.add(WHITE, 6, NOW);

            // 操作：白を 4 個入れる
            cart.add(WHITE, 4, NOW);

            // 確認：10 個
            assertThat(cart.itemsAt(NOW).getFirst().quantity()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("品目の数の上限")
    class ItemLimit {

        @Test
        @DisplayName("AC-75 30 品目のカートに、31 品目めは入れられない")
        void rejects31stItem() {
            // 準備：30 品目のカート
            var cart = newCart();
            fill(cart, UUID.randomUUID());

            // 操作と確認：業務のエラー TOO_MANY_CART_ITEMS で拒否される
            assertBusinessError(() -> cart.add(UUID.randomUUID(), 1, NOW), CartErrorCode.TOO_MANY_CART_ITEMS);
        }

        @Test
        @DisplayName("AC-75 30 品目のカートでも、カートにあるバリアントは合算できる")
        void mergesExistingItemWhenFull() {
            // 準備：30 品目のカート
            var cart = newCart();
            var first = UUID.randomUUID();
            fill(cart, first);

            // 操作：カートにあるバリアントを入れる
            cart.add(first, 1, NOW);

            // 確認：30 品目のまま
            assertThat(cart.itemsAt(NOW)).hasSize(30);
        }
    }

    @Nested
    @DisplayName("数量の変更と削除")
    class ChangeAndRemove {

        @Test
        @DisplayName("AC-78 品目の数量を変更できる")
        void changesQuantity() {
            // 準備：白 2 個、黒 1 個のカート
            var cart = whiteAndBlack();

            // 操作：白を 7 個にする
            cart.changeQuantity(WHITE, 7, NOW);

            // 確認：白だけが 7 個
            assertThat(cart.itemsAt(NOW)).extracting(CartItem::variantId, CartItem::quantity)
                    .containsExactly(tuple(WHITE, 7), tuple(BLACK, 1));
        }

        @Test
        @DisplayName("AC-78 品目を削除できる")
        void removesItem() {
            // 準備：白 2 個、黒 1 個のカート
            var cart = whiteAndBlack();

            // 操作：黒を削除する
            cart.remove(BLACK, NOW);

            // 確認：白だけが残る
            assertThat(cart.itemsAt(NOW)).extracting(CartItem::variantId, CartItem::quantity)
                    .containsExactly(tuple(WHITE, 2));
        }

        @Test
        @DisplayName("AC-78 削除した品目をもう一度削除しても、何も起きない")
        void removesIdempotently() {
            // 準備：白 2 個、黒 1 個のカート
            var cart = whiteAndBlack();

            // 操作：黒を 2 回削除する
            cart.remove(BLACK, NOW);
            cart.remove(BLACK, NOW);

            // 確認：白だけが残る
            assertThat(cart.itemsAt(NOW)).extracting(CartItem::variantId, CartItem::quantity)
                    .containsExactly(tuple(WHITE, 2));
        }

        @Test
        @DisplayName("AC-78 カートにない品目の数量は変更できない")
        void rejectsChangingMissingItem() {
            // 準備：黒を削除したカート
            var cart = whiteAndBlack();
            cart.remove(BLACK, NOW);

            // 操作と確認：業務のエラー NOT_FOUND で拒否される
            assertBusinessError(() -> cart.changeQuantity(BLACK, 1, NOW), CommonErrorCode.NOT_FOUND);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {0, -1, 11})
        @DisplayName("AC-78 1〜10 の外の数量への変更は拒否される")
        void rejectsChangingOutOfRange(int quantity) {
            // 準備：白 2 個、黒 1 個のカート
            var cart = whiteAndBlack();

            // 操作と確認：業務のエラー INVALID_CART_QUANTITY で拒否される
            assertBusinessError(() -> cart.changeQuantity(WHITE, quantity, NOW), CartErrorCode.INVALID_CART_QUANTITY);
        }
    }

    @Nested
    @DisplayName("注文した品目の除去")
    class RemoveOrdered {

        @Test
        @DisplayName("AC-147 注文した品目を一度に除くと、他の品目は残る")
        void removesOnlyGivenItems() {
            // 準備：白、黒、他の 3 品目のカート
            var cart = newCart();
            var other = UUID.randomUUID();
            addThree(cart, other);

            // 操作：白と黒を一度に除く
            cart.removeAll(List.of(WHITE, BLACK), NOW);

            // 確認：他の品目だけ
            assertThat(cart.itemsAt(NOW)).extracting(CartItem::variantId).containsExactly(other);
        }

        @Test
        @DisplayName("AC-147 複数の品目を一度に除くと、バージョンは 1 だけ上がる")
        void incrementsVersionOnce() {
            // 準備：3 品目を入れて、バージョンが 3 のカート
            var cart = newCart();
            addThree(cart, UUID.randomUUID());

            // 操作：白と黒を一度に除く
            cart.removeAll(List.of(WHITE, BLACK), NOW);

            // 確認：1 だけ上がる（保存の楽観ロックは、1 つ前のバージョンと比べるため）
            assertThat(cart.version()).isEqualTo(4);
        }

        @Test
        @DisplayName("AC-147 カートにない品目だけを除いても、バージョンは上がらない")
        void keepsVersionWithoutChange() {
            // 準備：白と黒を除いて、バージョンが 4 のカート
            var cart = newCart();
            addThree(cart, UUID.randomUUID());
            cart.removeAll(List.of(WHITE, BLACK), NOW);

            // 操作：もうない白と、入れていないバリアントを除く
            cart.removeAll(List.of(WHITE, UUID.randomUUID()), NOW);

            // 確認：4 のまま
            assertThat(cart.version()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("カートを空にする")
    class Clear {

        @Test
        @DisplayName("カートを空にすると、品目がなくなる")
        void removesAllItems() {
            // 準備：白 2 個のカート
            var cart = newCart();
            cart.add(WHITE, 2, NOW);

            // 操作：空にする
            cart.clear(NOW);

            // 確認：品目がない
            assertThat(cart.itemsAt(NOW)).isEmpty();
        }

        @Test
        @DisplayName("カートを空にすると、バージョンが上がる")
        void incrementsVersion() {
            // 準備：白 2 個を入れて、バージョンが 1 のカート
            var cart = newCart();
            cart.add(WHITE, 2, NOW);

            // 操作：空にする
            cart.clear(NOW);

            // 確認：2
            assertThat(cart.version()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("バージョン")
    class Version {

        @Test
        @DisplayName("追加・変更・削除のたびに、バージョンが上がる")
        void incrementsOnEachChange() {
            // 準備：空のカート
            var cart = newCart();

            // 操作：追加、変更、削除をする
            cart.add(WHITE, 1, NOW);
            cart.changeQuantity(WHITE, 2, NOW);
            cart.remove(WHITE, NOW);

            // 確認：3 回上がる
            assertThat(cart.version()).isEqualTo(3);
        }

        @Test
        @DisplayName("何も変わらない削除では、バージョンが上がらない")
        void keepsOnNoOpRemove() {
            // 準備：追加、変更、削除をして、バージョンが 3 のカート
            var cart = newCart();
            cart.add(WHITE, 1, NOW);
            cart.changeQuantity(WHITE, 2, NOW);
            cart.remove(WHITE, NOW);

            // 操作：もうない品目を削除する
            cart.remove(WHITE, NOW);

            // 確認：3 のまま
            assertThat(cart.version()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("カートの期限")
    class Expiry {

        @Test
        @DisplayName("AC-81 最後の変更から 30 日の 1 秒前までは、品目が残る")
        void keepsItemsJustBeforeExpiry() {
            // 準備：白 2 個のカート
            var cart = newCart();
            cart.add(WHITE, 2, NOW);

            // 操作と確認：30 日の 1 秒前には、品目がある
            assertThat(cart.itemsAt(NOW.plus(EXPIRY).minusSeconds(1))).hasSize(1);
        }

        @Test
        @DisplayName("AC-81 最後の変更から 30 日たったカートは、空として扱う")
        void treatsExpiredCartAsEmpty() {
            // 準備：白 2 個のカート
            var cart = newCart();
            cart.add(WHITE, 2, NOW);

            // 操作と確認：30 日たつと、品目がない
            assertThat(cart.itemsAt(NOW.plus(EXPIRY))).isEmpty();
        }

        @Test
        @DisplayName("AC-81 期限の切れたカートに入れた後の数量は、古い品目を数えない")
        void ignoresExpiredItemsInQuantityAfterAdding() {
            // 準備：白 2 個のカート
            var cart = newCart();
            cart.add(WHITE, 2, NOW);

            // 操作と確認：30 日たってから白を 1 個入れた後は 1 個
            assertThat(cart.quantityAfterAdding(WHITE, 1, NOW.plus(EXPIRY))).isEqualTo(1);
        }

        @Test
        @DisplayName("AC-81 期限の切れたカートに入れると、古い品目は消える")
        void dropsExpiredItemsOnAdd() {
            // 準備：白 2 個のカート
            var cart = newCart();
            cart.add(WHITE, 2, NOW);
            var expired = NOW.plus(EXPIRY);

            // 操作：30 日たってから黒を入れる
            cart.add(BLACK, 1, expired);

            // 確認：黒だけ
            assertThat(cart.itemsAt(expired)).extracting(CartItem::variantId).containsExactly(BLACK);
        }

        @Test
        @DisplayName("AC-81 期限の切れたカートの品目を削除しても、品目もバージョンも変わらない")
        void removeOnExpiredCartChangesNothing() {
            // 準備：白 2 個、黒 1 個のカート（バージョン 2）
            var cart = whiteAndBlack();

            // 操作：30 日たってから白を削除する
            cart.remove(WHITE, NOW.plus(EXPIRY));

            // 確認：期限の前から見た品目は白・黒のままで、バージョンは 2 のまま
            assertThat(cart.itemsAt(NOW)).extracting(CartItem::variantId).containsExactly(WHITE, BLACK);
            assertThat(cart.version()).isEqualTo(2);
        }

        @Test
        @DisplayName("AC-81 期限の切れたカートから品目を一度に除いても、品目もバージョンも変わらない")
        void removeAllOnExpiredCartChangesNothing() {
            // 準備：白 2 個、黒 1 個のカート（バージョン 2）
            var cart = whiteAndBlack();

            // 操作：30 日たってから白と黒を一度に除く
            cart.removeAll(List.of(WHITE, BLACK), NOW.plus(EXPIRY));

            // 確認：期限の前から見た品目は白・黒のままで、バージョンは 2 のまま
            assertThat(cart.itemsAt(NOW)).extracting(CartItem::variantId).containsExactly(WHITE, BLACK);
            assertThat(cart.version()).isEqualTo(2);
        }

        @Test
        @DisplayName("AC-81 ある時刻より前に期限が切れたカートは、最後の変更がその時刻の 30 日前より前のもの")
        void findsLastUpdateOfCartsExpiredBefore() {
            // 操作と確認：30 日後より前に切れたカートは、最後の変更が今より前
            assertThat(Cart.lastUpdateExpiredBefore(NOW.plus(EXPIRY))).isEqualTo(NOW);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * {@link #NOW} に空のカートを作る。
     *
     * @return カート
     */
    private static Cart newCart() {
        return Cart.create(UUID.randomUUID(), NOW);
    }

    /**
     * 白 2 個、黒 1 個の順に入れたカートを作る。
     *
     * @return カート
     */
    private static Cart whiteAndBlack() {
        var cart = newCart();
        cart.add(WHITE, 2, NOW);
        cart.add(BLACK, 1, NOW);
        return cart;
    }

    /**
     * 白 1 個、黒 2 個、他の 1 個の順に入れる。
     *
     * @param cart  入れるカート
     * @param other 他のバリアント ID（実行ごとに作るので、定数にできない）
     */
    private static void addThree(Cart cart, UUID other) {
        cart.add(WHITE, 1, NOW);
        cart.add(BLACK, 2, NOW);
        cart.add(other, 1, NOW);
    }

    /**
     * カートを、先頭のバリアントと他の 29 のバリアントの 30 品目にする。
     *
     * @param cart  入れるカート
     * @param first 先頭のバリアント ID（実行ごとに作るので、定数にできない）
     */
    private static void fill(Cart cart, UUID first) {
        cart.add(first, 1, NOW);
        for (int i = 1; i < 30; i++) {
            cart.add(UUID.randomUUID(), 1, NOW);
        }
    }
}
