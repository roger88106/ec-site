package com.ecsite.module.cart.domain;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.model.id.CartId;
import com.ecsite.common.model.id.VariantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
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
     * カートの品目の数の上限
     */
    private static final int MAX_ITEMS = Cart.MAX_ITEMS;

    /**
     * 白のバリアント ID
     */
    private static final VariantId WHITE = new VariantId(UUID.randomUUID());

    /**
     * 黒のバリアント ID
     */
    private static final VariantId BLACK = new VariantId(UUID.randomUUID());

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
        @ValueSource(ints = {0, -1})
        @DisplayName("AC-75 下限の 1 より少ない数量は拒否される")
        void rejectsQuantityBelowMin(int quantity) {
            // 準備：空のカート
            var cart = newCart();

            // 操作と確認：業務のエラー INVALID_CART_QUANTITY で拒否される
            assertBusinessError(() -> cart.add(WHITE, quantity, NOW), CartErrorCode.INVALID_CART_QUANTITY);
        }

        @Test
        @DisplayName("AC-75 上限の 10 を 1 つ超える数量 11 は拒否される")
        void rejectsQuantityOneOverMax() {
            // 準備：空のカート
            var cart = newCart();

            // 操作と確認：業務のエラー INVALID_CART_QUANTITY で拒否される
            assertBusinessError(() -> cart.add(WHITE, Cart.MAX_QUANTITY + 1, NOW), CartErrorCode.INVALID_CART_QUANTITY);
        }

        @Test
        @DisplayName("AC-75 下限ちょうどの数量 1 は入れられる")
        void acceptsQuantityAtMin() {
            // 準備：空のカート
            var cart = newCart();

            // 操作：白を 1 個入れる
            cart.add(WHITE, 1, NOW);

            // 確認：1 個になる
            assertThat(cart.itemsAt(NOW).getFirst().quantity()).isEqualTo(1);
        }

        @Test
        @DisplayName("AC-75 上限ちょうどの数量 10 は入れられる")
        void acceptsQuantityAtMax() {
            // 準備：空のカート
            var cart = newCart();

            // 操作：白を 10 個入れる
            cart.add(WHITE, Cart.MAX_QUANTITY, NOW);

            // 確認：上限と同じ個数になる
            assertThat(cart.itemsAt(NOW).getFirst().quantity()).isEqualTo(Cart.MAX_QUANTITY);
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
            // 準備：白 6 個のカート
            var cart = newCart();
            cart.add(WHITE, 6, NOW);

            // 操作と確認（前提）：白 5 個の追加が拒否される
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
        @DisplayName("AC-75 29 品目のカートに、30 品目めは入れられる")
        void accepts30thItem() {
            // 準備：29 品目のカート
            var cart = newCart();
            addDistinct(cart, MAX_ITEMS - 1);

            // 操作：別のバリアントを入れる
            cart.add(WHITE, 1, NOW);

            // 確認：30 品目
            assertThat(cart.itemsAt(NOW)).hasSize(MAX_ITEMS);
        }

        @Test
        @DisplayName("AC-75 30 品目のカートに、31 品目めは入れられない")
        void rejects31stItem() {
            // 準備：30 品目のカート
            var cart = newCart();
            fill(cart, new VariantId(UUID.randomUUID()));

            // 操作と確認：業務のエラー TOO_MANY_CART_ITEMS で拒否される
            assertBusinessError(() -> cart.add(new VariantId(UUID.randomUUID()), 1, NOW), CartErrorCode.TOO_MANY_CART_ITEMS);
        }

        @Test
        @DisplayName("AC-75 30 品目のカートでも、カートにあるバリアントは合算できる")
        void mergesExistingItemWhenFull() {
            // 準備：30 品目のカート
            var cart = newCart();
            var first = new VariantId(UUID.randomUUID());
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
        @ValueSource(ints = {0, -1})
        @DisplayName("AC-78 下限の 1 より少ない数量への変更は拒否される")
        void rejectsChangingBelowMin(int quantity) {
            // 準備：白 2 個、黒 1 個のカート
            var cart = whiteAndBlack();

            // 操作と確認：業務のエラー INVALID_CART_QUANTITY で拒否される
            assertBusinessError(() -> cart.changeQuantity(WHITE, quantity, NOW), CartErrorCode.INVALID_CART_QUANTITY);
        }

        @Test
        @DisplayName("AC-78 上限の 10 を 1 つ超える数量 11 への変更は拒否される")
        void rejectsChangingOneOverMax() {
            // 準備：白 2 個、黒 1 個のカート
            var cart = whiteAndBlack();

            // 操作と確認：業務のエラー INVALID_CART_QUANTITY で拒否される
            assertBusinessError(() -> cart.changeQuantity(WHITE, Cart.MAX_QUANTITY + 1, NOW), CartErrorCode.INVALID_CART_QUANTITY);
        }

        @Test
        @DisplayName("AC-78 下限ちょうどの数量 1 へは変更できる")
        void changesToMinQuantity() {
            // 準備：白 2 個、黒 1 個のカート
            var cart = whiteAndBlack();
            // 操作：白の数量を 1 にする
            cart.changeQuantity(WHITE, 1, NOW);

            // 確認：白が 1 個になる
            assertThat(cart.itemsAt(NOW)).filteredOn(item -> item.variantId().equals(WHITE))
                    .singleElement().extracting(CartItem::quantity).isEqualTo(1);
        }

        @Test
        @DisplayName("AC-78 上限ちょうどの数量 10 へは変更できる")
        void changesToMaxQuantity() {
            // 準備：白 2 個、黒 1 個のカート
            var cart = whiteAndBlack();
            // 操作：白の数量を 10 にする
            cart.changeQuantity(WHITE, Cart.MAX_QUANTITY, NOW);

            // 確認：白が 10 個になる
            assertThat(cart.itemsAt(NOW)).filteredOn(item -> item.variantId().equals(WHITE))
                    .singleElement().extracting(CartItem::quantity).isEqualTo(Cart.MAX_QUANTITY);
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
            var other = new VariantId(UUID.randomUUID());
            addThree(cart, other);

            // 操作：白と黒を一度に除く
            cart.removeAll(List.of(WHITE, BLACK), NOW, NOW);

            // 確認：他の品目だけ
            assertThat(cart.itemsAt(NOW)).extracting(CartItem::variantId).containsExactly(other);
        }

        @Test
        @DisplayName("AC-147 複数の品目を一度に除くと、バージョンは 1 だけ上がる")
        void incrementsVersionOnce() {
            // 準備：3 品目を入れて、バージョンが 3 のカート
            var cart = newCart();
            addThree(cart, new VariantId(UUID.randomUUID()));

            // 操作：白と黒を一度に除く
            cart.removeAll(List.of(WHITE, BLACK), NOW, NOW);

            // 確認：1 だけ上がる（保存の楽観ロックは、1 つ前のバージョンと比べるため）
            assertThat(cart.version()).isEqualTo(4);
        }

        @Test
        @DisplayName("AC-147 カートにない品目だけを除いても、バージョンは上がらない")
        void keepsVersionWithoutChange() {
            // 準備：白と黒を除いて、バージョンが 4 のカート
            var cart = newCart();
            addThree(cart, new VariantId(UUID.randomUUID()));
            cart.removeAll(List.of(WHITE, BLACK), NOW, NOW);

            // 操作：もうない白と、入れていないバリアントを除く
            cart.removeAll(List.of(WHITE, new VariantId(UUID.randomUUID())), NOW, NOW);

            // 確認：4 のまま
            assertThat(cart.version()).isEqualTo(4);
        }

        @Test
        @DisplayName("入れた日時が上限より後の品目は除かず、上限と同じ時刻の品目は除く")
        void keepsItemsAddedAfterLimit() {
            // 準備：NOW に白、1 秒後に黒を入れたカート
            var cart = newCart();
            cart.add(WHITE, 1, NOW);
            cart.add(BLACK, 1, NOW.plusSeconds(1));

            // 操作：上限を NOW にして、白と黒を除く
            cart.removeAll(List.of(WHITE, BLACK), NOW, NOW.plusSeconds(2));

            // 確認：上限と同じ時刻の白は除かれ、後に入れた黒は残る
            assertThat(cart.itemsAt(NOW.plusSeconds(2))).extracting(CartItem::variantId).containsExactly(BLACK);
        }

        @Test
        @DisplayName("上限より前に入れた品目は、上限の後に数量を変えていても除く")
        void removesItemWhoseQuantityChangedAfterLimit() {
            // 準備：NOW に白を入れ、1 秒後に数量を変えたカート
            var cart = newCart();
            cart.add(WHITE, 1, NOW);
            cart.changeQuantity(WHITE, 3, NOW.plusSeconds(1));

            // 操作：上限を NOW にして、白を除く
            cart.removeAll(List.of(WHITE), NOW, NOW.plusSeconds(2));

            // 確認：入れた日時は変わらないので、除かれる
            assertThat(cart.itemsAt(NOW.plusSeconds(2))).isEmpty();
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
            cart.removeAll(List.of(WHITE, BLACK), NOW, NOW.plus(EXPIRY));

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
        return Cart.create(new CartId(UUID.randomUUID()), NOW);
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
    private static void addThree(Cart cart, VariantId other) {
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
    private static void fill(Cart cart, VariantId first) {
        cart.add(first, 1, NOW);
        addDistinct(cart, MAX_ITEMS - 1);
    }

    /**
     * 別々のバリアントを 1 個ずつ、指定した品目の数だけ入れる。
     *
     * @param cart  入れるカート
     * @param count 品目の数
     */
    private static void addDistinct(Cart cart, int count) {
        for (int i = 0; i < count; i++) {
            cart.add(new VariantId(UUID.randomUUID()), 1, NOW);
        }
    }
}
