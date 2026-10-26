package com.ecsite.module.cart.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * カートを移す処理の単体テスト（docs/design/modules/cart.md）
 */
class CartMergeTest {

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = Instant.parse("2026-10-12T03:00:00Z");

    /**
     * 品目を入れた時刻（移す時刻より前）
     */
    private static final Instant EARLIER = NOW.minus(Duration.ofHours(1));

    private static final UUID WHITE = UUID.randomUUID();
    private static final UUID BLACK = UUID.randomUUID();
    private static final UUID RED = UUID.randomUUID();

    @Test
    @DisplayName("AC-122 移すカートの品目を、入れた順に会員のカートの後ろへ入れ、同じバリアントは数量を合算する")
    void movesItemsAndMergesSameVariant() {
        var member = memberCart();
        member.add(WHITE, 2, EARLIER);
        var guest = guestCart();
        guest.add(BLACK, 1, EARLIER);
        guest.add(WHITE, 3, EARLIER);

        var result = member.mergeFrom(guest, NOW);

        assertThat(member.itemsAt(NOW)).extracting(CartItem::variantId, CartItem::quantity)
                .containsExactly(tuple(WHITE, 5), tuple(BLACK, 1));
        assertThat(result.truncatedItems()).isEmpty();
        assertThat(result.skippedItems()).isEmpty();
    }

    @Test
    @DisplayName("AC-122 移し終えたら、移したカートは空になる。会員のカートも移したカートもバージョンが上がる")
    void clearsSourceCart() {
        var member = memberCart();
        var guest = guestCart();
        guest.add(WHITE, 1, EARLIER);
        var memberVersion = member.version();
        var guestVersion = guest.version();

        member.mergeFrom(guest, NOW);

        assertThat(guest.itemsAt(NOW)).isEmpty();
        assertThat(member.version()).isGreaterThan(memberVersion);
        assertThat(guest.version()).isGreaterThan(guestVersion);
    }

    @Test
    @DisplayName("AC-122 空のカートを移しても（2 回目の移す処理）、どちらのカートも変わらない")
    void mergingEmptyCartChangesNothing() {
        var member = memberCart();
        member.add(WHITE, 2, EARLIER);
        var guest = guestCart();
        guest.add(BLACK, 1, EARLIER);
        member.mergeFrom(guest, NOW);
        var memberVersion = member.version();
        var guestVersion = guest.version();

        var result = member.mergeFrom(guest, NOW);

        assertThat(member.version()).isEqualTo(memberVersion);
        assertThat(guest.version()).isEqualTo(guestVersion);
        assertThat(member.itemsAt(NOW)).extracting(CartItem::variantId, CartItem::quantity)
                .containsExactly(tuple(WHITE, 2), tuple(BLACK, 1));
        assertThat(result.truncatedItems()).isEmpty();
        assertThat(result.skippedItems()).isEmpty();
    }

    @Test
    @DisplayName("AC-123 合算して 10 を超えたら 10 に切り詰め、切り詰めた品目（切り詰めた後の数量）を返す")
    void truncatesQuantityToTen() {
        var member = memberCart();
        member.add(WHITE, 7, EARLIER);
        member.add(BLACK, 4, EARLIER);
        var guest = guestCart();
        guest.add(WHITE, 5, EARLIER);
        guest.add(BLACK, 6, EARLIER);

        var result = member.mergeFrom(guest, NOW);

        assertThat(member.itemsAt(NOW)).extracting(CartItem::variantId, CartItem::quantity)
                .containsExactly(tuple(WHITE, 10), tuple(BLACK, 10));
        // BLACK は合算でちょうど 10 なので、切り詰めていない
        assertThat(result.truncatedItems()).extracting(CartMergeResult.Item::variantId, CartMergeResult.Item::quantity)
                .containsExactly(tuple(WHITE, 10));
        assertThat(result.skippedItems()).isEmpty();
    }

    @Test
    @DisplayName("AC-123 品目が 30 に達したら、移すカートの後ろの品目から移さず、移さなかった品目（移す元の数量）を返す")
    void skipsItemsBeyondThirty() {
        var member = memberCart();
        var existing = variants(28);
        existing.forEach(variantId -> member.add(variantId, 1, EARLIER));
        var guest = guestCart();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var third = UUID.randomUUID();
        var fourth = UUID.randomUUID();
        guest.add(first, 1, EARLIER);
        guest.add(second, 2, EARLIER);
        guest.add(third, 3, EARLIER);
        guest.add(fourth, 4, EARLIER);

        var result = member.mergeFrom(guest, NOW);

        assertThat(member.itemsAt(NOW)).hasSize(30);
        assertThat(member.itemsAt(NOW).subList(28, 30)).extracting(CartItem::variantId).containsExactly(first, second);
        assertThat(result.skippedItems()).extracting(CartMergeResult.Item::variantId, CartMergeResult.Item::quantity)
                .containsExactly(tuple(third, 3), tuple(fourth, 4));
        assertThat(guest.itemsAt(NOW)).isEmpty();
    }

    @Test
    @DisplayName("AC-123 品目が 30 でも、会員のカートにあるバリアントは合算できる")
    void mergesExistingVariantEvenWhenFull() {
        var member = memberCart();
        var existing = variants(30);
        existing.forEach(variantId -> member.add(variantId, 1, EARLIER));
        var guest = guestCart();
        guest.add(RED, 1, EARLIER);
        guest.add(existing.getFirst(), 2, EARLIER);

        var result = member.mergeFrom(guest, NOW);

        assertThat(member.itemsAt(NOW)).hasSize(30);
        assertThat(member.itemsAt(NOW).getFirst().quantity()).isEqualTo(3);
        assertThat(result.skippedItems()).extracting(CartMergeResult.Item::variantId).containsExactly(RED);
    }

    @Test
    @DisplayName("AC-81 期限の切れたカートは空として扱う（移すカートは何も移さず、会員のカートは品目を消してから入れる）")
    void treatsExpiredCartAsEmpty() {
        var longAgo = NOW.minus(Duration.ofDays(31));
        var expiredGuest = guestCart();
        expiredGuest.add(WHITE, 1, longAgo);
        var member = memberCart();

        member.mergeFrom(expiredGuest, NOW);

        assertThat(member.itemsAt(NOW)).isEmpty();

        var expiredMember = memberCart();
        expiredMember.add(BLACK, 1, longAgo);
        var guest = guestCart();
        guest.add(WHITE, 2, EARLIER);

        expiredMember.mergeFrom(guest, NOW);

        assertThat(expiredMember.itemsAt(NOW)).extracting(CartItem::variantId, CartItem::quantity)
                .containsExactly(tuple(WHITE, 2));
    }

    @Test
    @DisplayName("会員のカートは会員を持ち、ログインせずに作ったカートは持たない")
    void memberOfCart() {
        var memberId = UUID.randomUUID();

        assertThat(Cart.createForMember(UUID.randomUUID(), memberId, NOW).memberId()).contains(memberId);
        assertThat(Cart.create(UUID.randomUUID(), NOW).memberId()).isEmpty();
    }

    private static Cart memberCart() {
        return Cart.createForMember(UUID.randomUUID(), UUID.randomUUID(), EARLIER.minus(Duration.ofDays(40)));
    }

    private static Cart guestCart() {
        return Cart.create(UUID.randomUUID(), EARLIER.minus(Duration.ofDays(40)));
    }

    private static List<UUID> variants(int count) {
        return new ArrayList<>(IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList());
    }
}
