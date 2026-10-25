package com.ecsite.module.cart.infrastructure.persistence;

import com.ecsite.IntegrationTest;
import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.module.cart.application.port.CartRepository;
import com.ecsite.module.cart.domain.Cart;
import com.ecsite.module.cart.domain.CartItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * カートのリポジトリの統合テスト。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class JooqCartRepositoryTest {

    /**
     * カートを作った時刻
     */
    private static final Instant CREATED_AT = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 品目を入れた時刻
     */
    private static final Instant ADDED_AT = Instant.parse("2026-10-07T15:10:00Z");

    /**
     * 品目を変えた時刻
     */
    private static final Instant CHANGED_AT = Instant.parse("2026-10-07T15:20:00Z");

    @Autowired
    CartRepository repository;

    @Test
    @DisplayName("ログインせずに作ったカートを保存し、会員を持たない空のカートとして、バージョンと最終更新日時を含めて再構築できる")
    void createsAndRestoresGuestCart() {
        var cart = Cart.create(UUID.randomUUID(), CREATED_AT);

        assertThat(repository.create(cart)).isTrue();

        var restored = repository.findById(cart.id()).orElseThrow();
        assertThat(restored.id()).isEqualTo(cart.id());
        assertThat(restored.memberId()).isEmpty();
        assertThat(restored.itemsAt(CREATED_AT)).isEmpty();
        assertThat(restored.version()).isZero();
        assertThat(restored.updatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("AC-121 会員のカートを保存すると、会員から引け、会員を持つカートとして再構築できる")
    void createsAndRestoresMemberCart() {
        var memberId = UUID.randomUUID();
        var cart = Cart.createForMember(UUID.randomUUID(), memberId, CREATED_AT);

        assertThat(repository.create(cart)).isTrue();

        assertThat(repository.findIdByMemberId(memberId)).contains(cart.id());
        assertThat(repository.findById(cart.id()).orElseThrow().memberId()).contains(memberId);
        assertThat(repository.findIdByMemberId(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("AC-121 同じ会員の 2 つ目のカートは保存せずに false を返し、会員のカートは先に作ったものだけ")
    void returnsFalseForSecondCartOfSameMember() {
        var memberId = UUID.randomUUID();
        var first = Cart.createForMember(UUID.randomUUID(), memberId, CREATED_AT);
        repository.create(first);
        var second = Cart.createForMember(UUID.randomUUID(), memberId, CREATED_AT);

        assertThat(repository.create(second)).isFalse();

        assertThat(repository.findIdByMemberId(memberId)).contains(first.id());
        assertThat(repository.findById(second.id())).isEmpty();
    }

    @Test
    @DisplayName("品目の変更（追加、数量の変更、削除）を 1 つずつ保存すると、品目が入れた順に置き換わり、バージョンと最終更新日時が変わる")
    void savesChangedItems() {
        var cart = Cart.create(UUID.randomUUID(), CREATED_AT);
        repository.create(cart);
        var shirt = UUID.randomUUID();
        var tea = UUID.randomUUID();
        var cup = UUID.randomUUID();
        // 保存はバージョンが 1 つ前のときだけ通るので、変更ごとに保存する
        cart.add(shirt, 1, ADDED_AT);
        repository.save(cart);
        cart.add(tea, 2, ADDED_AT);
        repository.save(cart);

        var loaded = repository.findById(cart.id()).orElseThrow();
        loaded.changeQuantity(tea, 5, CHANGED_AT);
        repository.save(loaded);
        loaded.remove(shirt, CHANGED_AT);
        repository.save(loaded);
        loaded.add(cup, 1, CHANGED_AT);
        repository.save(loaded);

        var restored = repository.findById(cart.id()).orElseThrow();
        assertThat(restored.itemsAt(CHANGED_AT)).containsExactly(
                new CartItem(tea, 5, ADDED_AT),
                new CartItem(cup, 1, CHANGED_AT));
        assertThat(restored.version()).isEqualTo(5);
        assertThat(restored.updatedAt()).isEqualTo(CHANGED_AT);
    }

    @Test
    @DisplayName("読み込んだ後に他で保存されたカートを保存すると CONCURRENT_MODIFICATION になり、先に保存した内容が残る")
    void rejectsSaveOfStaleVersion() {
        var cart = Cart.create(UUID.randomUUID(), CREATED_AT);
        repository.create(cart);
        var first = repository.findById(cart.id()).orElseThrow();
        var stale = repository.findById(cart.id()).orElseThrow();
        var shirt = UUID.randomUUID();
        first.add(shirt, 1, ADDED_AT);
        repository.save(first);

        stale.add(UUID.randomUUID(), 3, CHANGED_AT);

        assertBusinessError(() -> repository.save(stale), CommonErrorCode.CONCURRENT_MODIFICATION);
        var restored = repository.findById(cart.id()).orElseThrow();
        assertThat(restored.itemsAt(ADDED_AT)).containsExactly(new CartItem(shirt, 1, ADDED_AT));
        assertThat(restored.version()).isEqualTo(first.version());
    }
}
