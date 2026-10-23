package com.ecsite.module.catalog.api;

import com.ecsite.module.catalog.application.model.query.StoreProductListQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 店舗向けの商品の並び順を、application の並び順に変換するときの単体テスト
 */
class StoreCatalogApiSortTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(StoreProductSort.class)
    @DisplayName("AC-277 公開の並び順の全ての値を、application の同じ名前の値に写す")
    void mapsEverySort(StoreProductSort sort) {
        assertThat(StoreCatalogApi.toSort(sort)).isEqualTo(StoreProductListQuery.Sort.valueOf(sort.name()));
    }

    @Test
    @DisplayName("AC-277 並び順の指定がなければ、application でも指定なしにする")
    void keepsNullSort() {
        assertThat(StoreCatalogApi.toSort(null)).isNull();
    }
}
