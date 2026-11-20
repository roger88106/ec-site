package com.ecsite.module.cart.infrastructure.persistence;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.module.cart.application.port.CartRepository;
import com.ecsite.module.cart.domain.Cart;
import com.ecsite.module.cart.domain.CartItem;
import com.ecsite.support.config.IntegrationTest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static com.ecsite.jooq.cart.Tables.CART_;
import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * カートのリポジトリの統合テスト
 *
 * <p>カートの作成（会員ごとの一意を含む）、品目の変更の保存、楽観ロックを、DB を通して確認する。
 * カートの振る舞いそのものは {@code CartTest}、期限の切れたカートの削除は {@code CartCleanupJobTest} が確認する。
 *
 * <p>準備のデータと確認の期待値は、同じ定数から作る。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class JooqCartRepositoryTest {

    // ============================== 定数 ==============================

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

    /**
     * シャツのバリアント ID（カートの ID はテストごとに作るので、品目のバリアント ID は固定でよい）
     */
    private static final UUID SHIRT = UUID.fromString("00000000-0000-0000-0000-00000000c001");

    /**
     * お茶のバリアント ID
     */
    private static final UUID TEA = UUID.fromString("00000000-0000-0000-0000-00000000c002");

    /**
     * カップのバリアント ID
     */
    private static final UUID CUP = UUID.fromString("00000000-0000-0000-0000-00000000c003");

    /**
     * 品目の変更を 5 回保存した後のバージョン（作った直後は 0 で、変更ごとに 1 つ上がる）
     */
    private static final long VERSION_AFTER_FIVE_CHANGES = 5;

    /**
     * 品目の変更を 1 回保存した後のバージョン
     */
    private static final long VERSION_AFTER_ONE_CHANGE = 1;

    // ============================== Bean ==============================

    /**
     * テストするリポジトリ
     */
    @Autowired
    CartRepository repository;

    @Autowired
    DSLContext dsl;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("ログインせずに作ったカートの作成")
    class CreateGuestCart {

        @Test
        @DisplayName("保存すると、会員を持たない空のカートとして、バージョンと最終更新日時を含めて再構築できる")
        void createsAndRestoresGuestCart() {
            // 準備：ログインせずに作ったカート
            var cart = Cart.create(UUID.randomUUID(), CREATED_AT);

            // 操作：保存する
            repository.create(cart);

            // 確認：読み直すと、作った直後のカート
            assertRestoredAsNewGuestCart(repository.findById(cart.id()).orElseThrow(), cart.id());
        }

        @Test
        @DisplayName("会員のカートを渡すと、保存せずに例外にする")
        void rejectsMemberCart() {
            // 準備：会員のカート
            var cart = Cart.createForMember(UUID.randomUUID(), UUID.randomUUID(), CREATED_AT);

            // 操作と確認：ログインせずに使うカートの作成には使えない
            assertThatThrownBy(() -> repository.create(cart)).isInstanceOf(IllegalArgumentException.class);
            assertThat(repository.findById(cart.id())).isEmpty();
        }
    }

    @Nested
    @DisplayName("会員のカートの作成")
    class CreateMemberCart {

        @Test
        @DisplayName("AC-121 保存すると、会員からカートを引ける")
        void findsCartByMember() {
            // 準備：会員のカート
            var memberId = UUID.randomUUID();
            var cart = Cart.createForMember(UUID.randomUUID(), memberId, CREATED_AT);

            // 操作：保存する
            repository.createForMemberOrGetId(cart);

            // 確認：会員から引くと、このカート
            assertThat(repository.findIdByMemberId(memberId)).contains(cart.id());
        }

        @Test
        @DisplayName("AC-121 ほかの会員からは、カートを引けない")
        void doesNotFindCartByOtherMember() {
            // 準備：会員のカートを保存する
            repository.createForMemberOrGetId(Cart.createForMember(UUID.randomUUID(), UUID.randomUUID(), CREATED_AT));

            // 操作と確認：別の会員からは何も引けない
            assertThat(repository.findIdByMemberId(UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("AC-121 保存すると、会員を持つカートとして再構築できる")
        void restoresMemberOfCart() {
            // 準備：会員のカートを保存する
            var memberId = UUID.randomUUID();
            var cart = Cart.createForMember(UUID.randomUUID(), memberId, CREATED_AT);
            repository.createForMemberOrGetId(cart);

            // 操作：読み直す
            var restored = repository.findById(cart.id()).orElseThrow();

            // 確認：会員を持つ
            assertThat(restored.memberId()).contains(memberId);
        }

        @Test
        @DisplayName("AC-121 会員のカートがないときは、作ったカートの ID を返す")
        void createForMemberOrGetIdReturnsNewId() {
            // 準備：会員のカート
            var memberId = UUID.randomUUID();
            var cart = Cart.createForMember(UUID.randomUUID(), memberId, CREATED_AT);

            // 操作：1 つの文で作って ID を得る
            var id = repository.createForMemberOrGetId(cart);

            // 確認：渡したカートの ID で、会員から引ける
            assertThat(id).isEqualTo(cart.id());
            assertThat(repository.findIdByMemberId(memberId)).contains(cart.id());
        }

        @Test
        @DisplayName("AC-121 同じ会員のカートがすでにあれば、先のカートの ID を返し、既存の行の値を変えない")
        void createForMemberOrGetIdReturnsExistingIdAndKeepsRow() {
            // 準備：会員のカートを作り、品目を入れて保存してバージョンを 1 にし、行の全ての値を控える
            var memberId = UUID.randomUUID();
            var first = Cart.createForMember(UUID.randomUUID(), memberId, CREATED_AT);
            repository.createForMemberOrGetId(first);
            first.add(SHIRT, 1, ADDED_AT);
            repository.save(first);
            var before = dsl.selectFrom(CART_).where(CART_.ID.eq(first.id())).fetchOne();

            // 操作：同じ会員で、別の ID、バージョン 0、別の時刻のカートを渡す（例外にならない）
            var id = repository.createForMemberOrGetId(Cart.createForMember(UUID.randomUUID(), memberId, CHANGED_AT));

            // 確認：先のカートの ID を返し、行の値（バージョン、作成日時、更新日時を含む）は変わらず、会員のカートは 1 つのまま
            assertThat(id).isEqualTo(first.id());
            assertThat(dsl.selectFrom(CART_).where(CART_.ID.eq(first.id())).fetchOne()).isEqualTo(before);
            assertThat(dsl.fetchCount(CART_, CART_.MEMBER_ID.eq(memberId))).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("品目の変更の保存")
    class SaveItems {

        @Test
        @DisplayName("追加・数量の変更・削除を 1 つずつ保存すると、品目が入れた順に置き換わる")
        void replacesItemsInOrder() {
            // 準備：品目の変更を 1 つずつ保存したカート
            var cartId = cartChangedFiveTimes();

            // 操作：読み直す
            var restored = repository.findById(cartId).orElseThrow();

            // 確認：削除したシャツはなく、数量を変えたお茶（入れた時刻のまま）、後から入れたカップの順
            assertThat(restored.itemsAt(CHANGED_AT)).containsExactly(
                    new CartItem(TEA, 5, ADDED_AT),
                    new CartItem(CUP, 1, CHANGED_AT));
        }

        @Test
        @DisplayName("品目の変更を保存すると、バージョンと最終更新日時が変わる")
        void updatesVersionAndUpdatedAt() {
            // 準備：品目の変更を 1 つずつ 5 回保存したカート
            var cartId = cartChangedFiveTimes();

            // 操作：読み直す
            var restored = repository.findById(cartId).orElseThrow();

            // 確認：バージョンは 5 で、最終更新日時は最後の変更の時刻
            assertThat(restored.version()).isEqualTo(VERSION_AFTER_FIVE_CHANGES);
            assertThat(restored.updatedAt()).isEqualTo(CHANGED_AT);
        }
    }

    @Nested
    @DisplayName("楽観ロック")
    class OptimisticLock {

        @Test
        @DisplayName("読み込んだ後に他で保存されたカートを保存すると、CONCURRENT_MODIFICATION になる")
        void rejectsSaveOfStaleVersion() {
            // 準備：同じカートを 2 回読み込み、先に読んだ方にシャツを入れて保存し、後に読んだ方も変える
            var carts = cartWithStaleCopy();

            // 操作と確認：古い方の保存は、同時の変更のエラー
            assertBusinessError(() -> repository.save(carts.stale()), CommonErrorCode.CONCURRENT_MODIFICATION);
        }

        @Test
        @DisplayName("古いカートの保存に失敗しても、先に保存した内容が残る")
        void keepsFirstSave() {
            // 準備：同じカートを 2 回読み込み、先に読んだ方にシャツを入れて保存し、後に読んだ方も変える
            var carts = cartWithStaleCopy();

            // 操作と確認（前提）：古い方の保存は失敗する
            assertBusinessError(() -> repository.save(carts.stale()), CommonErrorCode.CONCURRENT_MODIFICATION);

            // 確認：先に保存したシャツ 1 個と、そのバージョンが残る
            var restored = repository.findById(carts.id()).orElseThrow();
            assertThat(restored.itemsAt(ADDED_AT)).containsExactly(new CartItem(SHIRT, 1, ADDED_AT));
            assertThat(restored.version()).isEqualTo(VERSION_AFTER_ONE_CHANGE);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 読み直したカートが、作った直後のログインせずに作ったカートであることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>カート ID</li>
     *   <li>会員を持たない</li>
     *   <li>品目が空</li>
     *   <li>バージョンが 0</li>
     *   <li>最終更新日時が作った時刻</li>
     * </ul>
     *
     * @param restored 読み直したカート
     * @param cartId   保存したカートの ID（実行ごとに作るので、定数にできない）
     */
    private static void assertRestoredAsNewGuestCart(Cart restored, UUID cartId) {
        assertThat(restored.id()).isEqualTo(cartId);
        assertThat(restored.memberId()).isEmpty();
        assertThat(restored.itemsAt(CREATED_AT)).isEmpty();
        assertThat(restored.version()).isZero();
        assertThat(restored.updatedAt()).isEqualTo(CREATED_AT);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 保存した後に古くなった読み込みを持つカート
     *
     * @param id    カート ID
     * @param stale 古い読み込み（他の読み込みの保存の後に、品目を変えたもの）
     */
    private record StaleCart(UUID id, Cart stale) {
    }

    /**
     * 品目の変更を 1 つずつ 5 回保存したカートを作る。
     *
     * <p>シャツ 1 とお茶 2 を入れて保存し、読み直してから、お茶を 5 に変え、シャツを削除し、カップ 1 を入れて、それぞれ保存する。
     * 保存はバージョンが 1 つ前のときだけ通るので、変更ごとに保存する。
     *
     * @return カート ID
     */
    private UUID cartChangedFiveTimes() {
        var cart = Cart.create(UUID.randomUUID(), CREATED_AT);
        repository.create(cart);
        cart.add(SHIRT, 1, ADDED_AT);
        repository.save(cart);
        cart.add(TEA, 2, ADDED_AT);
        repository.save(cart);

        var loaded = repository.findById(cart.id()).orElseThrow();
        loaded.changeQuantity(TEA, 5, CHANGED_AT);
        repository.save(loaded);
        loaded.remove(SHIRT, CHANGED_AT);
        repository.save(loaded);
        loaded.add(CUP, 1, CHANGED_AT);
        repository.save(loaded);
        return cart.id();
    }

    /**
     * 同じカートを 2 回読み込み、先に読んだ方にシャツ 1 を入れて保存し、後に読んだ方にも別の品目を入れる。
     *
     * @return カート ID と、古い読み込み
     */
    private StaleCart cartWithStaleCopy() {
        var cart = Cart.create(UUID.randomUUID(), CREATED_AT);
        repository.create(cart);
        var first = repository.findById(cart.id()).orElseThrow();
        var stale = repository.findById(cart.id()).orElseThrow();
        first.add(SHIRT, 1, ADDED_AT);
        repository.save(first);
        stale.add(UUID.randomUUID(), 3, CHANGED_AT);
        return new StaleCart(cart.id(), stale);
    }
}
