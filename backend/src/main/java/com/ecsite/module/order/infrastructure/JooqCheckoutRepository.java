package com.ecsite.module.order.infrastructure;

import com.ecsite.jooq.ordering.tables.records.CheckoutRecord;
import com.ecsite.module.order.domain.checkout.Checkout;
import com.ecsite.module.order.domain.checkout.port.CheckoutRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.ecsite.jooq.ordering.Tables.CHECKOUT;

/**
 * 注文手続きのリポジトリ
 */
@Repository
@RequiredArgsConstructor
class JooqCheckoutRepository implements CheckoutRepository {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    @Override
    public void insert(Checkout checkout) {
        dsl.insertInto(CHECKOUT)
                .set(CHECKOUT.ID, checkout.id())
                .set(CHECKOUT.CART_ID, checkout.cartId())
                .set(CHECKOUT.CART_VERSION, checkout.cartVersion())
                .set(CHECKOUT.EXPIRES_AT, checkout.expiresAt())
                .set(CHECKOUT.CREATED_AT, checkout.createdAt())
                .execute();
    }

    @Override
    public Optional<Checkout> findById(UUID id) {
        return dsl.selectFrom(CHECKOUT).where(CHECKOUT.ID.eq(id)).fetchOptional().map(JooqCheckoutRepository::toCheckout);
    }

    @Override
    public Optional<Checkout> lockById(UUID id) {
        return dsl.selectFrom(CHECKOUT).where(CHECKOUT.ID.eq(id)).forUpdate().fetchOptional()
                .map(JooqCheckoutRepository::toCheckout);
    }

    @Override
    public List<UUID> findIdsByCartId(UUID cartId) {
        return dsl.select(CHECKOUT.ID).from(CHECKOUT).where(CHECKOUT.CART_ID.eq(cartId)).fetch(CHECKOUT.ID);
    }

    @Override
    public Optional<UUID> findLatestIdByCartId(UUID cartId) {
        return dsl.select(CHECKOUT.ID)
                .from(CHECKOUT)
                .where(CHECKOUT.CART_ID.eq(cartId))
                .orderBy(CHECKOUT.CREATED_AT.desc(), CHECKOUT.ID)
                .limit(1)
                .fetchOptional(CHECKOUT.ID);
    }

    /**
     * レコードから注文手続きを作る。
     */
    private static Checkout toCheckout(CheckoutRecord record) {
        return new Checkout(record.getId(), record.getCartId(), record.getCartVersion(), record.getExpiresAt(),
                record.getCreatedAt());
    }
}
