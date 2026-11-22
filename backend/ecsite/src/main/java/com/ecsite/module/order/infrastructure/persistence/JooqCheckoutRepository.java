package com.ecsite.module.order.infrastructure.persistence;

import com.ecsite.common.model.base.RestoreGuard;
import com.ecsite.common.model.id.CartId;
import com.ecsite.common.model.id.MemberId;
import com.ecsite.common.model.id.OrderId;
import com.ecsite.jooq.ordering.tables.records.CheckoutRecord;
import com.ecsite.module.order.domain.checkout.Checkout;
import com.ecsite.module.order.domain.checkout.CheckoutId;
import com.ecsite.module.order.domain.checkout.port.CheckoutRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.ecsite.jooq.ordering.Tables.CHECKOUT;

/**
 * 注文手続きのリポジトリ
 */
@Repository
@RequiredArgsConstructor
class JooqCheckoutRepository implements CheckoutRepository {

    /**
     * 集約の名前
     *
     * <p>戻すときの失敗のメッセージに書く。
     */
    private static final String AGGREGATE_NAME = "checkout";

    /**
     * 確認した注文の ID の型（jsonb の読み書き）
     */
    private static final TypeReference<Set<UUID>> ORDER_IDS = new TypeReference<>() {
    };

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * 確認した注文の ID を jsonb にするための JSON の変換
     */
    private final JsonMapper jsonMapper;

    /**
     * 注文手続きを追加する。
     */
    @Override
    public void insert(Checkout checkout) {
        dsl.insertInto(CHECKOUT)
                .set(CHECKOUT.ID, checkout.id().value())
                .set(CHECKOUT.MEMBER_ID, checkout.memberId().value())
                .set(CHECKOUT.CART_ID, checkout.cartId().value())
                .set(CHECKOUT.CART_VERSION, checkout.cartVersion())
                .set(CHECKOUT.EXPIRES_AT, checkout.expiresAt())
                .set(CHECKOUT.CREATED_AT, checkout.createdAt())
                .set(CHECKOUT.ACKNOWLEDGED_ORDER_IDS, JSONB.valueOf(jsonMapper.writeValueAsString(toUuids(checkout.acknowledgedOrderIds()))))
                .execute();
    }

    /**
     * 注文手続きを ID で探す。
     */
    @Override
    public Optional<Checkout> findById(CheckoutId id) {
        return dsl.selectFrom(CHECKOUT).where(CHECKOUT.ID.eq(id.value())).fetchOptional().map(this::toCheckout);
    }

    /**
     * 注文手続きを行ロックして探す。
     */
    @Override
    public Optional<Checkout> lockById(CheckoutId id) {
        return dsl.selectFrom(CHECKOUT).where(CHECKOUT.ID.eq(id.value())).forUpdate().fetchOptional()
                .map(this::toCheckout);
    }

    /**
     * カートの注文手続きの ID を返す。
     */
    @Override
    public List<CheckoutId> findIdsByCartId(CartId cartId) {
        return dsl.select(CHECKOUT.ID).from(CHECKOUT).where(CHECKOUT.CART_ID.eq(cartId.value())).fetch(CHECKOUT.ID)
                .stream().map(CheckoutId::new).toList();
    }

    /**
     * カートの注文手続きのうち、指定したバージョンより古いカートのバージョンで始めたものの ID を返す。
     */
    @Override
    public List<CheckoutId> findIdsByCartIdStartedBefore(CartId cartId, long cartVersion) {
        return dsl.select(CHECKOUT.ID).from(CHECKOUT)
                .where(CHECKOUT.CART_ID.eq(cartId.value()).and(CHECKOUT.CART_VERSION.lt(cartVersion)))
                .fetch(CHECKOUT.ID).stream().map(CheckoutId::new).toList();
    }

    /**
     * カートの最新の注文手続きの ID を返す。
     *
     * <p>同時刻なら ID の順で決める。
     */
    @Override
    public Optional<CheckoutId> findLatestIdByCartId(CartId cartId) {
        return dsl.select(CHECKOUT.ID)
                .from(CHECKOUT)
                .where(CHECKOUT.CART_ID.eq(cartId.value()))
                .orderBy(CHECKOUT.CREATED_AT.desc(), CHECKOUT.ID)
                .limit(1)
                .fetchOptional(CHECKOUT.ID)
                .map(CheckoutId::new);
    }

    /**
     * 期限が指定した時刻より前の注文手続きを、件数の上限まで消す。
     *
     * <p>他のトランザクションがロックしている行（注文の途中）は飛ばす。
     */
    @Override
    public int deleteExpiredBefore(Instant expiredBefore, int limit) {
        // 選ぶのと消すのを 1 文にする。ロックされた行は、次の回で消す
        // ※選ぶ行は MATERIALIZED の CTE で 1 回だけ確定させる。IN の副問い合わせに直接書くと、実行計画によっては
        //   外側の行ごとに再評価され、前の評価で消した行を飛ばして次の行を選ぶので、件数の上限より多く消しうる
        var targets = DSL.name("targets").fields("id").asMaterialized(DSL.select(CHECKOUT.ID)
                .from(CHECKOUT)
                .where(CHECKOUT.EXPIRES_AT.lt(expiredBefore))
                .limit(limit)
                .forUpdate()
                .skipLocked());
        return dsl.with(targets)
                .delete(CHECKOUT)
                .where(CHECKOUT.ID.in(DSL.select(targets.field("id", UUID.class)).from(targets)))
                .execute();
    }

    /**
     * レコードから注文手続きを作る。
     *
     * <p>全体を、集約を戻す共通の補助の中で作る（不変条件の違反と、読めない JSON を、復元の失敗にする）。
     */
    private Checkout toCheckout(CheckoutRecord record) {
        return RestoreGuard.restore(AGGREGATE_NAME, () -> new Checkout(new CheckoutId(record.getId()), new MemberId(record.getMemberId()), new CartId(record.getCartId()),
                record.getCartVersion(), record.getExpiresAt(), record.getCreatedAt(),
                toOrderIds(jsonMapper.readValue(record.getAcknowledgedOrderIds().data(), ORDER_IDS))));
    }

    /**
     * 注文の識別子を、jsonb に書く UUID にする。
     *
     * @param orderIds 注文の識別子
     * @return 注文 ID
     */
    private static Set<UUID> toUuids(Set<OrderId> orderIds) {
        return orderIds.stream().map(OrderId::value).collect(Collectors.toSet());
    }

    /**
     * jsonb から読んだ UUID を、注文の識別子にする。
     *
     * @param orderIds 注文 ID
     * @return 注文の識別子
     */
    private static Set<OrderId> toOrderIds(Set<UUID> orderIds) {
        return orderIds.stream().map(OrderId::new).collect(Collectors.toSet());
    }
}
