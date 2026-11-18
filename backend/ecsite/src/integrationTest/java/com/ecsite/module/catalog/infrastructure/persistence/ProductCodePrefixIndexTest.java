package com.ecsite.module.catalog.infrastructure.persistence;

import com.ecsite.common.paging.PageRequest;
import com.ecsite.module.catalog.application.model.query.ProductListQuery;
import com.ecsite.module.catalog.application.port.MediaDirectory;
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
 * 品番の絞り込みが使う索引の統合テスト
 *
 * <p>管理画面の商品一覧で品番を絞り込むと、商品品番とバリアント品番の一意制約の索引を、範囲の条件（Index Cond）で使うことを、実行計画で確認する。
 * 索引の名前だけでは足りない。シーケンシャルスキャンを無効にすると、照合順序が C でない列でも、索引を全て読んで Filter で絞る計画になり、索引の名前が計画に出るため。
 *
 * <p>条件の式はテストに書かず、{@code JooqProductQueryRepository#findSummaries} が実行する SQL を集めて検査する。
 * 理由：絞り込みの SQL の形を変えても、同じテストで確認するため。テストのためにリポジトリの可視性を広げないため。
 *
 * <p>行がなくても、シーケンシャルスキャンを無効にすれば、使える索引があれば計画に選ばれる。テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class ProductCodePrefixIndexTest {

    // ============================== 定数 ==============================

    /**
     * 商品品番の一意制約の索引の名前
     */
    private static final String PRODUCT_CODE_INDEX = "product_product_code_key";

    /**
     * バリアント品番の一意制約の索引の名前
     */
    private static final String VARIANT_CODE_INDEX = "variant_variant_code_key";

    /**
     * 絞り込みに使う品番の前半
     */
    private static final String CODE_PREFIX = "ABC";

    /**
     * 索引を範囲の条件で使う計画を探す正規表現の書式
     *
     * <p>索引の名前がある行の次の行に、Index Cond と列の名前が出る形。索引を全て読んで Filter で絞る計画には、Index Cond の行がない。
     * 引数は、索引の名前、列の名前。
     */
    private static final String INDEX_RANGE_CONDITION_FORMAT = "(?s)%s[^\\n]*\\n[^\\n]*Index Cond:[^\\n]*%s";

    @Autowired
    private DSLContext dsl;

    @Autowired
    private MediaDirectory mediaDirectory;

    // ============================== テスト ==============================

    @Test
    @DisplayName("品番の絞り込みは、商品品番とバリアント品番の一意制約の索引を、範囲の条件で使う")
    void codeFilterUsesBothCodeIndexesWithRangeCondition() {
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
        var repository = new JooqProductQueryRepository(recording, mediaDirectory);

        // 操作：品番だけを指定して一覧を取得し、実行された各 Query（一覧と件数）の実行計画を取る
        repository.findSummaries(new ProductListQuery(CODE_PREFIX, null, null, null), PageRequest.of(1, 20));
        List<String> plans = executed.stream().map(query -> dsl.explain(query).plan()).toList();

        // 確認：実行された Query があり、各計画に、二つの索引の範囲の条件に、それぞれの品番の列が出る
        assertThat(plans).as("実行された Query の実行計画").isNotEmpty();
        for (String plan : plans) {
            assertThat(plan)
                    .as("品番の絞り込みの実行計画（索引 %s と %s の範囲の条件を使うはず）%n%s",
                            PRODUCT_CODE_INDEX, VARIANT_CODE_INDEX, plan)
                    .containsPattern(Pattern.compile(
                            INDEX_RANGE_CONDITION_FORMAT.formatted(PRODUCT_CODE_INDEX, "product_code")))
                    .containsPattern(Pattern.compile(
                            INDEX_RANGE_CONDITION_FORMAT.formatted(VARIANT_CODE_INDEX, "variant_code")));
        }
    }
}
