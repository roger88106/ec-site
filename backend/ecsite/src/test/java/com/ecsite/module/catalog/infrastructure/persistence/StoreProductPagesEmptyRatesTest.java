package com.ecsite.module.catalog.infrastructure.persistence;

import com.ecsite.common.paging.PageRequest;
import com.ecsite.module.catalog.application.model.query.StoreProductListQuery;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * C 側の商品の一覧と検索で、有効な税率がないときの単体テスト
 *
 * <p>確認すること：
 * <ul>
 *   <li>どの商品も税込価格を出せないので、DB に問い合わせず、空のページを返す</li>
 * </ul>
 */
class StoreProductPagesEmptyRatesTest {

    private static final PageRequest REQUEST = PageRequest.of(1, 20);

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    @DisplayName("税率がないとき、一覧は空のページを返す")
    void listIsEmpty() {
        // 準備
        var dsl = mock(DSLContext.class);
        var repository = new JooqStoreProductQueryRepository(dsl, null, new StoreProductPages(dsl, null));
        var query = new StoreProductListQuery(null, null, null, StoreProductListQuery.Sort.NEWEST);

        // 操作
        var page = repository.findVisible(query, REQUEST, NOW, Map.of());

        // 確認
        assertThat(page.items()).isEmpty();
        assertThat(page.totalItems()).isZero();
        verifyNoInteractions(dsl);
    }

    @Test
    @DisplayName("税率がないとき、検索は空のページを返す")
    void searchIsEmpty() {
        // 準備
        var dsl = mock(DSLContext.class);
        var search = new PostgresProductSearch(new StoreProductPages(dsl, null));

        // 操作
        var page = search.search("綿", REQUEST, NOW, Map.of());

        // 確認
        assertThat(page.items()).isEmpty();
        assertThat(page.totalItems()).isZero();
        verifyNoInteractions(dsl);
    }
}
