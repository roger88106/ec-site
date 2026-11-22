package com.ecsite.module.cart.infrastructure.persistence;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.id.CartId;
import com.ecsite.common.model.id.MemberId;
import com.ecsite.common.model.id.VariantId;
import com.ecsite.jooq.cart.tables.records.CartRecord;
import com.ecsite.module.cart.application.port.CartRepository;
import com.ecsite.module.cart.domain.Cart;
import com.ecsite.module.cart.domain.CartItem;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static com.ecsite.jooq.cart.Tables.CART_;
import static com.ecsite.jooq.cart.Tables.CART_ITEM;

/**
 * カートのリポジトリ
 */
@Repository
@RequiredArgsConstructor
class JooqCartRepository implements CartRepository {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * 時計（作成日時）
     */
    private final Clock clock;

    /**
     * ログインせずに使うカートを作る。
     *
     * @param cart 会員を持たない空のカート
     */
    @Override
    public void create(Cart cart) {
        if (cart.memberId().isPresent()) {
            throw new IllegalArgumentException("会員のカートは createForMemberOrGetId で作る");
        }
        dsl.insertInto(CART_)
                .set(CART_.ID, cart.id().value())
                .set(CART_.VERSION, cart.version())
                .set(CART_.CREATED_AT, clock.instant())
                .set(CART_.UPDATED_AT, cart.updatedAt())
                .execute();
    }

    /**
     * 会員のカートを作り、カート ID を返す。
     *
     * <p>同じ会員のカートが既にあれば、そのカート ID を返し、他の列（バージョン、作成日時、更新日時）は変えない。
     *
     * @param cart 会員の空のカート
     * @return カート ID
     */
    @Override
    public CartId createForMemberOrGetId(Cart cart) {
        // 衝突したときは、会員 ID を同じ値で書き換えて RETURNING で既存の行の ID を得る（DO NOTHING では行が返らない）。他の列は書き換えない
        var id = dsl.insertInto(CART_)
                .set(CART_.ID, cart.id().value())
                .set(CART_.MEMBER_ID, cart.memberId().orElseThrow().value())
                .set(CART_.VERSION, cart.version())
                .set(CART_.CREATED_AT, clock.instant())
                .set(CART_.UPDATED_AT, cart.updatedAt())
                .onConflict(CART_.MEMBER_ID)
                .doUpdate()
                .set(CART_.MEMBER_ID, DSL.excluded(CART_.MEMBER_ID))
                .returning(CART_.ID)
                .fetchSingle()
                .getId();
        return new CartId(id);
    }

    /**
     * 会員のカートの ID を探す。
     *
     * @param memberId 会員 ID
     * @return カート ID。なければ空
     */
    @Override
    public Optional<CartId> findIdByMemberId(MemberId memberId) {
        return dsl.select(CART_.ID).from(CART_).where(CART_.MEMBER_ID.eq(memberId.value())).fetchOptional(CART_.ID)
                .map(CartId::new);
    }

    /**
     * カートを取得する。
     *
     * <p>品目は入れた順（連番の順）。
     *
     * @param id カート ID
     * @return カート。なければ空
     */
    @Override
    public Optional<Cart> findById(CartId id) {
        return dsl.selectFrom(CART_).where(CART_.ID.eq(id.value())).fetchOptional().map(this::toCart);
    }

    /**
     * カートの変更を保存する。
     *
     * <p>バージョンが 1 つ前のときだけ更新し（楽観ロック）、品目は消してから入れた順に入れ直す（連番も入れた順に振り直される）。
     *
     * @param cart 変更したカート
     * @throws BusinessException 読み込んだ後に他で変更された（{@code CONCURRENT_MODIFICATION}）
     */
    @Override
    public void save(Cart cart) {
        int updated = dsl.update(CART_)
                .set(CART_.VERSION, cart.version())
                .set(CART_.UPDATED_AT, cart.updatedAt())
                .where(CART_.ID.eq(cart.id().value()))
                .and(CART_.VERSION.eq(cart.version() - 1))
                .execute();
        if (updated == 0) {
            throw BusinessException.of(CommonErrorCode.CONCURRENT_MODIFICATION);
        }
        dsl.deleteFrom(CART_ITEM).where(CART_ITEM.CART_ID.eq(cart.id().value())).execute();
        var items = cart.itemsAt(cart.updatedAt());
        if (!items.isEmpty()) {
            var insert = dsl.insertInto(CART_ITEM, CART_ITEM.CART_ID, CART_ITEM.VARIANT_ID, CART_ITEM.QUANTITY, CART_ITEM.ADDED_AT);
            for (var item : items) {
                insert = insert.values(cart.id().value(), item.variantId().value(), item.quantity(), item.addedAt());
            }
            insert.execute();
        }
    }

    /**
     * 最後の変更が指定した時刻より前のカートを、件数の上限まで消す。
     *
     * <p>品目は外部キーの連鎖で消える。
     *
     * @param updatedBefore 最後の変更の時刻の上限
     * @param limit         消す件数の上限
     * @return 消した件数
     */
    @Override
    public int deleteUpdatedBefore(Instant updatedBefore, int limit) {
        // 選ぶのと消すのを 1 文にする。他のトランザクションがロックしている行は飛ばし、次の回で消す
        return dsl.deleteFrom(CART_)
                .where(CART_.ID.in(dsl.select(CART_.ID)
                        .from(CART_)
                        .where(CART_.UPDATED_AT.lt(updatedBefore))
                        .limit(limit)
                        .forUpdate()
                        .skipLocked()))
                .execute();
    }

// ============================== プライベートメソッド ==============================

    /**
     * カートのレコードと、その品目から、カートを復元する。
     */
    private Cart toCart(CartRecord cart) {
        var items = dsl.selectFrom(CART_ITEM)
                .where(CART_ITEM.CART_ID.eq(cart.getId()))
                .orderBy(CART_ITEM.SEQUENCE_NO)
                .fetch(item -> new CartItem(new VariantId(item.getVariantId()), item.getQuantity(), item.getAddedAt()));
        var memberId = cart.getMemberId() == null ? null : new MemberId(cart.getMemberId());
        return new Cart(new CartId(cart.getId()), memberId, items, cart.getVersion(), cart.getUpdatedAt());
    }
}
