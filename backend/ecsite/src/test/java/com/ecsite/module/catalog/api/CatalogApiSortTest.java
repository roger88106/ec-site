package com.ecsite.module.catalog.api;

import com.ecsite.module.catalog.api.model.ShownProductSort;
import com.ecsite.module.catalog.application.model.query.StoreProductListQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 店舗向けの商品の並び順を、application の並び順に変換するときの単体テスト
 */
class CatalogApiSortTest {

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(ShownProductSort.class)
    @DisplayName("AC-277 公開の並び順の全ての値を、application の同じ名前の値に写す")
    void mapsEverySort(ShownProductSort sort) {
        // 操作と確認：同じ名前の値になる
        assertThat(CatalogApi.toSort(sort)).isEqualTo(StoreProductListQuery.Sort.valueOf(sort.name()));
    }
}
