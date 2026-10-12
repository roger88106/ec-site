package com.ecsite.module.cart.infrastructure;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.cart.service.Cart;
import com.ecsite.module.cart.service.CartItem;
import com.ecsite.module.cart.service.CartRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

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
     * カートを作る。会員のカートは、同じ会員のカートがすでにあれば作らない（一意制約の違反でトランザクションを壊さない）。
     *
     * @param cart 空のカート
     */
    @Override
    public void create(Cart cart) {
        dsl.insertInto(CART_)
                .set(CART_.ID, cart.id())
                .set(CART_.MEMBER_ID, cart.memberId().orElse(null))
                .set(CART_.VERSION, cart.version())
                .set(CART_.CREATED_AT, clock.instant())
                .set(CART_.UPDATED_AT, cart.updatedAt())
                .onConflict(CART_.MEMBER_ID)
                .doNothing()
                .execute();
    }

    @Override
    public Optional<UUID> findIdByMemberId(UUID memberId) {
        return dsl.select(CART_.ID).from(CART_).where(CART_.MEMBER_ID.eq(memberId)).fetchOptional(CART_.ID);
    }

    /**
     * カートを取得する。品目は入れた順（連番の順）。
     *
     * @param id カート ID
     * @return カート
     */
    @Override
    public Optional<Cart> findById(UUID id) {
        return dsl.selectFrom(CART_)
                .where(CART_.ID.eq(id))
                .fetchOptional()
                .map(cart -> new Cart(cart.getId(), cart.getMemberId(),
                        dsl.selectFrom(CART_ITEM)
                                .where(CART_ITEM.CART_ID.eq(id))
                                .orderBy(CART_ITEM.SEQUENCE_NO)
                                .fetch(item -> new CartItem(item.getVariantId(), item.getQuantity(), item.getAddedAt())),
                        cart.getVersion(),
                        cart.getUpdatedAt()));
    }

    /**
     * カートの変更を保存する。<br />
     * バージョンが 1 つ前のときだけ更新し（楽観ロック）、品目は消してから入れた順に入れ直す（連番も入れた順に振り直される）。
     *
     * @param cart 変更したカート
     */
    @Override
    public void save(Cart cart) {
        int updated = dsl.update(CART_)
                .set(CART_.VERSION, cart.version())
                .set(CART_.UPDATED_AT, cart.updatedAt())
                .where(CART_.ID.eq(cart.id()))
                .and(CART_.VERSION.eq(cart.version() - 1))
                .execute();
        if (updated == 0) {
            throw BusinessException.of(CommonErrorCode.CONCURRENT_MODIFICATION);
        }
        dsl.deleteFrom(CART_ITEM).where(CART_ITEM.CART_ID.eq(cart.id())).execute();
        var items = cart.itemsAt(cart.updatedAt());
        if (!items.isEmpty()) {
            var insert = dsl.insertInto(CART_ITEM, CART_ITEM.CART_ID, CART_ITEM.VARIANT_ID, CART_ITEM.QUANTITY, CART_ITEM.ADDED_AT);
            items.forEach(item -> insert.values(cart.id(), item.variantId(), item.quantity(), item.addedAt()));
            insert.execute();
        }
    }
}
