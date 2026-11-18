package com.ecsite.module.order.infrastructure.persistence;

import com.ecsite.common.paging.PageRequest;
import com.ecsite.module.order.application.port.OrderQueryRepository.Filter;
import com.ecsite.support.config.IntegrationTest;
import org.jooq.DSLContext;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.jooq.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注文番号の前方一致の検索が使う索引の統合テスト
 *
 * <p>注文番号の前方一致の検索が、注文番号の一意制約の索引を、範囲の条件（Index Cond）で使うことを、実行計画で確認する。
 * 索引の名前だけでは足りない。シーケンシャルスキャンを無効にすると、照合順序が C でない列でも、索引を全て読んで Filter で絞る計画になり、索引の名前が計画に出るため。
 *
 * <p>条件の式はテストに書かず、{@code JooqOrderQueryRepository#findOrders} が実行する SQL を集めて検査する。
 * 理由：検索の SQL の形を変えても、同じテストで確認するため。テストのためにリポジトリの可視性を広げないため。
 *
 * <p>行がなくても、シーケンシャルスキャンを無効にすれば、使える索引があれば計画に選ばれる。テストごとにロールバックする。
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

    /**
     * 索引を範囲の条件で使う計画を探す正規表現
     *
     * <p>索引の名前がある行の次の行に、Index Cond と列の名前が出る形。索引を全て読んで Filter で絞る計画には、Index Cond の行がない。
     */
    private static final Pattern INDEX_RANGE_CONDITION =
            Pattern.compile("(?s)" + ORDER_NUMBER_INDEX + "[^\\n]*\\n[^\\n]*Index Cond:[^\\n]*order_number");

    @Autowired
    private DSLContext dsl;

    // ============================== テスト ==============================

    @Test
    @DisplayName("注文番号の前方一致の検索は、注文番号の一意制約の索引を、範囲の条件で使う")
    void prefixSearchUsesOrderNumberIndexWithRangeCondition() {
        // 準備：シーケンシャルスキャンを無効にし、索引を使える検索なら索引が選ばれるようにする。
        // 実行された Query を集める DSLContext でリポジトリを組む
        dsl.execute("SET LOCAL enable_seqscan = off");
        List<Query> executed = new ArrayList<>();
        DSLContext recording = dsl.configuration().derive(new ExecuteListener() {
            @Override
            public void executeStart(ExecuteContext ctx) {
                executed.add(ctx.query());
            }
        }).dsl();
        var repository = new JooqOrderQueryRepository(recording);

        // 操作：注文番号の前方一致だけを指定して一覧を取得し、実行された各 Query（一覧と件数）の実行計画を取る
        repository.findOrders(new Filter(null, ORDER_NUMBER_PREFIX, null, null, null, null), PageRequest.of(1, 20));
        List<String> plans = executed.stream().map(query -> dsl.explain(query).plan()).toList();

        // 確認：実行された Query があり、各計画で、一意制約の索引の範囲の条件に、注文番号の列が出る
        assertThat(plans).as("実行された Query の実行計画").isNotEmpty();
        for (String plan : plans) {
            assertThat(plan)
                    .as("注文番号の前方一致の検索の実行計画（索引 %s の範囲の条件を使うはず）%n%s", ORDER_NUMBER_INDEX, plan)
                    .containsPattern(INDEX_RANGE_CONDITION);
        }
    }
}
