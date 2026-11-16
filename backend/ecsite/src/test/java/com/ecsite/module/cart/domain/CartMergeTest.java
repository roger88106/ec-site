package com.ecsite.module.cart.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * カートを移す処理の単体テスト
 *
 * <p>ログインしたときに、ブラウザーのカートの品目を会員のカートに移す処理（{@link Cart#mergeFrom}）だけを確認する。
 * ログインの流れと移した結果の知らせは、会員のカートの API の統合テストと店舗画面のテストが確認する。
 */
class CartMergeTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-12 12:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-12T03:00:00Z");

    /**
     * 品目を入れた時刻（移す時刻より前）
     */
    private static final Instant EARLIER = NOW.minus(Duration.ofHours(1));

    /**
     * 期限が切れるほど前の、品目を入れた時刻
     */
    private static final Instant LONG_AGO = NOW.minus(Duration.ofDays(31));

    /**
     * 白のバリアント ID
     */
    private static final UUID WHITE = UUID.randomUUID();

    /**
     * 黒のバリアント ID
     */
    private static final UUID BLACK = UUID.randomUUID();

    /**
     * 赤のバリアント ID
     */
    private static final UUID RED = UUID.randomUUID();

    // ============================== テスト ==============================

    @Nested
    @DisplayName("品目を移す")
    class Move {

        @Test
        @DisplayName("AC-122 移すカートの品目を、入れた順に会員のカートの後ろへ入れ、同じバリアントは数量を合算する")
        void movesItemsAndMergesSameVariant() {
            // 準備：白 2 個の会員のカートと、黒 1 個、白 3 個の順のブラウザーのカート
            var member = memberCart();
            member.add(WHITE, 2, EARLIER);
            var guest = guestCart();
            guest.add(BLACK, 1, EARLIER);
            guest.add(WHITE, 3, EARLIER);

            // 操作：移す
            member.mergeFrom(guest, NOW);

            // 確認：白は元の位置で 5 個、黒は後ろに 1 個
            assertThat(member.itemsAt(NOW)).extracting(CartItem::variantId, CartItem::quantity)
                    .containsExactly(tuple(WHITE, 5), tuple(BLACK, 1));
        }

        @Test
        @DisplayName("AC-123 上限を超えずに移せたときは、切り詰めた品目も移さなかった品目もない")
        void reportsNothingWithinLimits() {
            // 準備：白 2 個の会員のカートと、黒 1 個、白 3 個のブラウザーのカート
            var member = memberCart();
            member.add(WHITE, 2, EARLIER);
            var guest = guestCart();
            guest.add(BLACK, 1, EARLIER);
            guest.add(WHITE, 3, EARLIER);

            // 操作：移す
            var result = member.mergeFrom(guest, NOW);

            // 確認：知らせる品目がない
            assertThat(result.truncatedItems()).isEmpty();
            assertThat(result.skippedItems()).isEmpty();
        }

        @Test
        @DisplayName("AC-122 移し終えたら、移したカートは空になる")
        void clearsSourceCart() {
            // 準備：白 1 個のブラウザーのカート
            var member = memberCart();
            var guest = guestCart();
            guest.add(WHITE, 1, EARLIER);

            // 操作：移す
            member.mergeFrom(guest, NOW);

            // 確認：ブラウザーのカートに品目がない
            assertThat(guest.itemsAt(NOW)).isEmpty();
        }

        @Test
        @DisplayName("AC-122 移すと、会員のカートのバージョンが上がる")
        void incrementsMemberVersion() {
            // 準備：白 1 個のブラウザーのカート
            var member = memberCart();
            var guest = guestCart();
            guest.add(WHITE, 1, EARLIER);
            var memberVersion = member.version();

            // 操作：移す
            member.mergeFrom(guest, NOW);

            // 確認：上がる
            assertThat(member.version()).isGreaterThan(memberVersion);
        }

        @Test
        @DisplayName("AC-122 移すと、移したカートのバージョンが上がる")
        void incrementsSourceVersion() {
            // 準備：白 1 個のブラウザーのカート
            var member = memberCart();
            var guest = guestCart();
            guest.add(WHITE, 1, EARLIER);
            var guestVersion = guest.version();

            // 操作：移す
            member.mergeFrom(guest, NOW);

            // 確認：上がる
            assertThat(guest.version()).isGreaterThan(guestVersion);
        }
    }

    @Nested
    @DisplayName("もう一度移す")
    class MoveAgain {

        @Test
        @DisplayName("AC-122 移した後のカートをもう一度移しても、どちらのバージョンも変わらない")
        void keepsVersions() {
            // 準備：一度移した後のカート
            var member = memberCart();
            var guest = mergedGuest(member);
            var memberVersion = member.version();
            var guestVersion = guest.version();

            // 操作：もう一度移す
            member.mergeFrom(guest, NOW);

            // 確認：どちらも変わらない
            assertThat(member.version()).isEqualTo(memberVersion);
            assertThat(guest.version()).isEqualTo(guestVersion);
        }

        @Test
        @DisplayName("AC-122 移した後のカートをもう一度移しても、会員のカートの品目は変わらない")
        void keepsMemberItems() {
            // 準備：一度移した後のカート
            var member = memberCart();
            var guest = mergedGuest(member);

            // 操作：もう一度移す
            member.mergeFrom(guest, NOW);

            // 確認：白 2 個、黒 1 個のまま
            assertThat(member.itemsAt(NOW)).extracting(CartItem::variantId, CartItem::quantity)
                    .containsExactly(tuple(WHITE, 2), tuple(BLACK, 1));
        }

        @Test
        @DisplayName("AC-122 移した後のカートをもう一度移しても、知らせる品目はない")
        void reportsNothing() {
            // 準備：一度移した後のカート
            var member = memberCart();
            var guest = mergedGuest(member);

            // 操作：もう一度移す
            var result = member.mergeFrom(guest, NOW);

            // 確認：切り詰めた品目も移さなかった品目もない
            assertThat(result.truncatedItems()).isEmpty();
            assertThat(result.skippedItems()).isEmpty();
        }
    }

    @Nested
    @DisplayName("数量の上限")
    class QuantityLimit {

        @Test
        @DisplayName("AC-123 合算して 10 を超えた品目は、10 に切り詰める")
        void truncatesQuantityToTen() {
            // 準備：合算で白は 12 個、黒はちょうど 10 個になるカート
            var member = memberCart();
            var guest = guestCart();
            addOverTen(member, guest);

            // 操作：移す
            member.mergeFrom(guest, NOW);

            // 確認：どちらも 10 個
            assertThat(member.itemsAt(NOW)).extracting(CartItem::variantId, CartItem::quantity)
                    .containsExactly(tuple(WHITE, 10), tuple(BLACK, 10));
        }

        @Test
        @DisplayName("AC-123 切り詰めた品目を、切り詰めた後の数量で返す")
        void reportsTruncatedItems() {
            // 準備：合算で白は 12 個、黒はちょうど 10 個になるカート
            var member = memberCart();
            var guest = guestCart();
            addOverTen(member, guest);

            // 操作：移す
            var result = member.mergeFrom(guest, NOW);

            // 確認：白だけ（黒は合算でちょうど 10 なので、切り詰めていない）
            assertThat(result.truncatedItems()).extracting(CartMergeResult.Item::variantId, CartMergeResult.Item::quantity)
                    .containsExactly(tuple(WHITE, 10));
        }

        @Test
        @DisplayName("AC-123 合算して上限を 1 超えた品目も、10 に切り詰めて、切り詰めた品目として返す")
        void truncatesQuantityOfEleven() {
            // 準備：合算で白が 11 個になるカート
            var member = memberCart();
            var guest = guestCart();
            member.add(WHITE, 6, EARLIER);
            guest.add(WHITE, 5, EARLIER);

            // 操作：移す
            var result = member.mergeFrom(guest, NOW);

            // 確認：白は 10 個
            assertThat(member.itemsAt(NOW)).extracting(CartItem::variantId, CartItem::quantity)
                    .containsExactly(tuple(WHITE, 10));

            // 確認：切り詰めた品目として返る
            assertThat(result.truncatedItems()).extracting(CartMergeResult.Item::variantId, CartMergeResult.Item::quantity)
                    .containsExactly(tuple(WHITE, 10));
        }

        @Test
        @DisplayName("AC-123 数量を切り詰めた品目は、移さなかった品目にしない")
        void doesNotSkipTruncatedItems() {
            // 準備：合算で白は 12 個、黒はちょうど 10 個になるカート
            var member = memberCart();
            var guest = guestCart();
            addOverTen(member, guest);

            // 操作：移す
            var result = member.mergeFrom(guest, NOW);

            // 確認：移さなかった品目がない
            assertThat(result.skippedItems()).isEmpty();
        }
    }

    @Nested
    @DisplayName("品目の数の上限")
    class ItemLimit {

        @Test
        @DisplayName("AC-123 品目が 30 に達したら、移すカートの前の品目から 30 まで移す")
        void movesUpToThirty() {
            // 準備：28 品目の会員のカートと、4 品目のブラウザーのカート
            var member = memberCart();
            addItems(member, 28);
            var guest = guestCart();
            var moving = addFour(guest);

            // 操作：移す
            member.mergeFrom(guest, NOW);

            // 確認：30 品目で、後ろの 2 つは移すカートの前の 2 つ
            assertThat(member.itemsAt(NOW)).hasSize(30);
            assertThat(member.itemsAt(NOW).subList(28, 30)).extracting(CartItem::variantId)
                    .containsExactly(moving.get(0), moving.get(1));
        }

        @Test
        @DisplayName("AC-123 30 品目を超える分は移さず、移さなかった品目を移す元の数量で返す")
        void reportsSkippedItems() {
            // 準備：28 品目の会員のカートと、数量が 1〜4 の 4 品目のブラウザーのカート
            var member = memberCart();
            addItems(member, 28);
            var guest = guestCart();
            var moving = addFour(guest);

            // 操作：移す
            var result = member.mergeFrom(guest, NOW);

            // 確認：後ろの 2 つを、元の数量 3 と 4 で返す
            assertThat(result.skippedItems()).extracting(CartMergeResult.Item::variantId, CartMergeResult.Item::quantity)
                    .containsExactly(tuple(moving.get(2), 3), tuple(moving.get(3), 4));
        }

        @Test
        @DisplayName("AC-122 移さなかった品目があっても、移したカートは空になる")
        void clearsSourceCartWithSkippedItems() {
            // 準備：28 品目の会員のカートと、4 品目のブラウザーのカート
            var member = memberCart();
            addItems(member, 28);
            var guest = guestCart();
            addFour(guest);

            // 操作：移す
            member.mergeFrom(guest, NOW);

            // 確認：ブラウザーのカートに品目がない
            assertThat(guest.itemsAt(NOW)).isEmpty();
        }

        @Test
        @DisplayName("AC-123 品目が 30 でも、会員のカートにあるバリアントは合算できる")
        void mergesExistingVariantEvenWhenFull() {
            // 準備：30 品目の会員のカートと、新しい赤と会員のカートの先頭のバリアントを入れたブラウザーのカート
            var member = memberCart();
            var existing = addItems(member, 30);
            var guest = guestCart();
            guest.add(RED, 1, EARLIER);
            guest.add(existing.getFirst(), 2, EARLIER);

            // 操作：移す
            member.mergeFrom(guest, NOW);

            // 確認：30 品目のまま、先頭が 3 個
            assertThat(member.itemsAt(NOW)).hasSize(30);
            assertThat(member.itemsAt(NOW).getFirst().quantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("AC-123 品目が 30 のときは、会員のカートにないバリアントを移さなかった品目として返す")
        void skipsNewVariantWhenFull() {
            // 準備：30 品目の会員のカートと、新しい赤と会員のカートの先頭のバリアントを入れたブラウザーのカート
            var member = memberCart();
            var existing = addItems(member, 30);
            var guest = guestCart();
            guest.add(RED, 1, EARLIER);
            guest.add(existing.getFirst(), 2, EARLIER);

            // 操作：移す
            var result = member.mergeFrom(guest, NOW);

            // 確認：赤だけ
            assertThat(result.skippedItems()).extracting(CartMergeResult.Item::variantId).containsExactly(RED);
        }
    }

    @Nested
    @DisplayName("期限の切れたカート")
    class Expired {

        @Test
        @DisplayName("AC-81 期限の切れたブラウザーのカートを移しても、何も移さない")
        void movesNothingFromExpiredCart() {
            // 準備：31 日前に白を入れたブラウザーのカート
            var expiredGuest = guestCart();
            expiredGuest.add(WHITE, 1, LONG_AGO);
            var member = memberCart();

            // 操作：移す
            member.mergeFrom(expiredGuest, NOW);

            // 確認：会員のカートは空
            assertThat(member.itemsAt(NOW)).isEmpty();
        }

        @Test
        @DisplayName("AC-81 期限の切れた会員のカートに移すと、古い品目を消してから入れる")
        void dropsExpiredMemberItems() {
            // 準備：31 日前に黒を入れた会員のカートと、白 2 個のブラウザーのカート
            var expiredMember = memberCart();
            expiredMember.add(BLACK, 1, LONG_AGO);
            var guest = guestCart();
            guest.add(WHITE, 2, EARLIER);

            // 操作：移す
            expiredMember.mergeFrom(guest, NOW);

            // 確認：白だけ
            assertThat(expiredMember.itemsAt(NOW)).extracting(CartItem::variantId, CartItem::quantity)
                    .containsExactly(tuple(WHITE, 2));
        }
    }

    @Nested
    @DisplayName("カートの会員")
    class Owner {

        @Test
        @DisplayName("会員のカートは会員を持つ")
        void memberCartHasMember() {
            // 準備：会員 ID
            var memberId = UUID.randomUUID();

            // 操作と確認：会員のカートはその会員を持つ
            assertThat(Cart.createForMember(UUID.randomUUID(), memberId, NOW).memberId()).contains(memberId);
        }

        @Test
        @DisplayName("ログインせずに作ったカートは会員を持たない")
        void guestCartHasNoMember() {
            // 操作と確認：会員がない
            assertThat(Cart.create(UUID.randomUUID(), NOW).memberId()).isEmpty();
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 会員のカートを作る。
     *
     * <p>作った時刻は、品目を入れる時刻の 40 日前。
     *
     * @return 会員のカート
     */
    private static Cart memberCart() {
        return Cart.createForMember(UUID.randomUUID(), UUID.randomUUID(), EARLIER.minus(Duration.ofDays(40)));
    }

    /**
     * ログインせずに使っていたブラウザーのカートを作る。
     *
     * <p>作った時刻は、品目を入れる時刻の 40 日前。
     *
     * @return ブラウザーのカート
     */
    private static Cart guestCart() {
        return Cart.create(UUID.randomUUID(), EARLIER.minus(Duration.ofDays(40)));
    }

    /**
     * 会員のカートに白 2 個を入れ、黒 1 個のブラウザーのカートを一度移す。
     *
     * @param member 会員のカート
     * @return 移した後のブラウザーのカート
     */
    private static Cart mergedGuest(Cart member) {
        member.add(WHITE, 2, EARLIER);
        var guest = guestCart();
        guest.add(BLACK, 1, EARLIER);
        member.mergeFrom(guest, NOW);
        return guest;
    }

    /**
     * 会員のカートに白 7 個、黒 4 個、ブラウザーのカートに白 5 個、黒 6 個を入れる。
     *
     * <p>合算すると、白は 12 個、黒はちょうど 10 個になる。
     *
     * @param member 会員のカート
     * @param guest  ブラウザーのカート
     */
    private static void addOverTen(Cart member, Cart guest) {
        member.add(WHITE, 7, EARLIER);
        member.add(BLACK, 4, EARLIER);
        guest.add(WHITE, 5, EARLIER);
        guest.add(BLACK, 6, EARLIER);
    }

    /**
     * 新しいバリアントを 1 個ずつ入れる。
     *
     * @param cart  入れるカート
     * @param count 入れる品目の数
     * @return 入れたバリアント ID（入れた順）
     */
    private static List<UUID> addItems(Cart cart, int count) {
        var variants = IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList();
        variants.forEach(variantId -> cart.add(variantId, 1, EARLIER));
        return variants;
    }

    /**
     * 新しい 4 つのバリアントを、数量 1、2、3、4 の順に入れる。
     *
     * @param cart 入れるカート
     * @return 入れたバリアント ID（入れた順。実行ごとに作るので、定数にできない）
     */
    private static List<UUID> addFour(Cart cart) {
        var variants = IntStream.range(0, 4).mapToObj(i -> UUID.randomUUID()).toList();
        for (int i = 0; i < variants.size(); i++) {
            cart.add(variants.get(i), i + 1, EARLIER);
        }
        return variants;
    }
}
