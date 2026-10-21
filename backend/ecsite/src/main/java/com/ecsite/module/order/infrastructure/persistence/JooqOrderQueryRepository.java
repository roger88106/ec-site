package com.ecsite.module.order.infrastructure.persistence;

import com.ecsite.common.model.value.Money;
import com.ecsite.common.paging.PageRequest;
import com.ecsite.common.paging.PageResult;
import com.ecsite.module.order.application.port.OrderQueryRepository;
import com.ecsite.module.order.domain.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;

/**
 * 注文の一覧のクエリリポジトリ
 */
@Repository
@RequiredArgsConstructor
class JooqOrderQueryRepository implements OrderQueryRepository {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * 注文を、注文日時の新しい順に取得する。
     *
     * @param filter  絞り込みの条件
     * @param request ページの指定
     * @return 注文の一覧
     */
    @Override
    public PageResult<OrderSummaryView> findOrders(Filter filter, PageRequest request) {
        var where = where(filter);
        var items = dsl.select(SALES_ORDER.ID, SALES_ORDER.ORDER_NUMBER, SALES_ORDER.ORDERED_AT,
                        SALES_ORDER.RECIPIENT_NAME, SALES_ORDER.TOTAL_AMOUNT, SALES_ORDER.STATUS)
                .from(SALES_ORDER)
                .where(where)
                // 同じ日時なら ID の順にし、ページをまたいでも重複・欠落が出ないようにする（API の規約）
                .orderBy(SALES_ORDER.ORDERED_AT.desc(), SALES_ORDER.ID)
                .limit(request.size())
                .offset(request.offset())
                .fetch(row -> new OrderSummaryView(row.value1(), row.value2(), row.value3(), row.value4(), Money.of(row.value5()),
                        OrderStatus.valueOf(row.value6())));
        return PageResult.of(items, request, dsl.fetchCount(SALES_ORDER, where));
    }

    /**
     * 絞り込みの条件を SQL の条件にする。注文番号の前方一致は、% や _ を文字として扱う。
     */
    private static Condition where(Filter filter) {
        var where = DSL.noCondition();
        if (filter.memberId() != null) {
            where = where.and(SALES_ORDER.MEMBER_ID.eq(filter.memberId()));
        }
        if (filter.orderNumberPrefix() != null) {
            where = where.and(SALES_ORDER.ORDER_NUMBER.startsWith(filter.orderNumberPrefix()));
        }
        if (filter.status() != null) {
            where = where.and(SALES_ORDER.STATUS.eq(filter.status().name()));
        }
        if (filter.orderedFrom() != null) {
            where = where.and(SALES_ORDER.ORDERED_AT.ge(filter.orderedFrom()));
        }
        if (filter.orderedUntil() != null) {
            where = where.and(SALES_ORDER.ORDERED_AT.lt(filter.orderedUntil()));
        }
        if (filter.orderIds() != null) {
            where = where.and(filter.orderIds().isEmpty() ? DSL.falseCondition() : SALES_ORDER.ID.in(filter.orderIds()));
        }
        return where;
    }
}
