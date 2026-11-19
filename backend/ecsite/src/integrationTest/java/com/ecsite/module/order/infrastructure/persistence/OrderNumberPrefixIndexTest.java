package com.ecsite.module.order.infrastructure.persistence;

import com.ecsite.common.paging.PageRequest;
import com.ecsite.module.order.application.port.OrderQueryRepository.Filter;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.persistence.QueryPlans;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注文番号の前方一致の検索が使う索引の統合テスト
 *
 * <p>注文番号の前方一致の検索が、注文番号の一意制約の索引を、範囲の条件（Index Cond）で使うことを、実行計画で確認する。
 * 確認の仕組みは {@link QueryPlans}。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class OrderNumberPrefixIndexTest {

    // ============================== 定数 ==============================

    /**
     * 注文番号の一意制約の索引の名前
     */
    private static final String ORDER_NUMBER_INDEX = "sales_order_order_number_key";

    /**
     * 前方一致の検索に使う、注文番号の日付の部分
     */
    private static final String ORDER_NUMBER_PREFIX = "20261118";

    @Autowired
    private DSLContext dsl;

    // ============================== テスト ==============================

    @Test
    @DisplayName("注文番号の前方一致の検索は、注文番号の一意制約の索引を、範囲の条件で使う")
    void prefixSearchUsesOrderNumberIndexWithRangeCondition() {
        // 操作：注文番号の前方一致だけを指定して一覧を取得し、実行された各 Query（一覧と件数）の実行計画を取る
        List<String> plans = QueryPlans.capture(dsl, recording -> new JooqOrderQueryRepository(recording)
                .findOrders(new Filter(null, ORDER_NUMBER_PREFIX, null, null, null, null), PageRequest.of(1, 20)));

        // 確認：実行された Query があり、各計画で、一意制約の索引の範囲の条件に、注文番号の列が出る
        assertThat(plans).as("実行された Query の実行計画").isNotEmpty();
        for (String plan : plans) {
            assertThat(plan)
                    .as("注文番号の前方一致の検索の実行計画（索引 %s の範囲の条件を使うはず）%n%s", ORDER_NUMBER_INDEX, plan)
                    .containsPattern(QueryPlans.indexRangeCondition(ORDER_NUMBER_INDEX, "order_number"));
        }
    }
}
