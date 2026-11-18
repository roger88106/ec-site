package com.ecsite.module.order.infrastructure.persistence;

import com.ecsite.support.config.IntegrationTest;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static com.ecsite.jooq.ordering.Tables.SALES_ORDER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注文番号の前方一致の検索が使う索引の統合テスト
 *
 * <p>注文番号の前方一致の検索が、注文番号の一意制約の索引を使うことを、実行計画で確認する。索引の名前だけでは足りない（シーケンシャルスキャンを無効にすると、照合順序が C でない列でも、索引を全て読む計画が選ばれる）ので、索引の範囲の条件（Index Cond）も確認する。
 * 検索の式は、アプリ（{@code JooqOrderQueryRepository}）と同じ {@code startsWith} を使う。理由：アプリの SQL と同じ式にして、
 * 索引を使えるかどうかを、アプリが実際に実行する検索で確認するため。
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
     * 索引の範囲の条件が計画に出るときの見出し
     *
     * <p>索引を全て読んで条件で絞る計画には出ない。
     */
    private static final String INDEX_RANGE_CONDITION = "Index Cond";

    /**
     * 前方一致の検索に使う、注文番号の日付の部分
     */
    private static final String ORDER_NUMBER_PREFIX = "20261118";

    @Autowired
    private DSLContext dsl;

    // ============================== テスト ==============================

    @Test
    @DisplayName("注文番号の前方一致の検索は、注文番号の一意制約の索引を使う")
    void prefixSearchUsesOrderNumberIndex() {
        // 準備：シーケンシャルスキャンを無効にし、索引を使える検索なら索引が選ばれるようにする。アプリと同じ式で検索を組む
        dsl.execute("SET LOCAL enable_seqscan = off");
        Select<Record1<String>> query = dsl.select(SALES_ORDER.ORDER_NUMBER)
                .from(SALES_ORDER)
                .where(SALES_ORDER.ORDER_NUMBER.startsWith(ORDER_NUMBER_PREFIX));

        // 操作：実行計画を取る
        String plan = dsl.explain(query).plan();

        // 確認：計画に、注文番号の一意制約の索引の名前と、索引の範囲の条件がある
        // （照合順序が C でないと、索引を全て読んで条件で絞る計画になり、索引の名前だけは計画に出る）
        assertThat(plan)
                .as("注文番号の前方一致の検索の実行計画（索引 %s の範囲の条件を使うはず）%n%s", ORDER_NUMBER_INDEX, plan)
                .contains(ORDER_NUMBER_INDEX)
                .contains(INDEX_RANGE_CONDITION);
    }
}
