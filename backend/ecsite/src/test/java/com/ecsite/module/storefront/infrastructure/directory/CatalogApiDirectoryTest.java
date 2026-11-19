package com.ecsite.module.storefront.infrastructure.directory;

import com.ecsite.module.catalog.api.model.ShownProductSort;
import com.ecsite.module.storefront.application.model.ProductCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商品カタログの型と店舗の型を変換するときの、列挙の変換の単体テスト
 *
 * <p>列挙の値の対応だけを確認する。商品を店舗の画面に返す流れは、店舗の API の統合テストが確認する。
 */
class CatalogApiDirectoryTest {

    // ============================== テスト ==============================

    @ParameterizedTest(name = "「{0}」")
    @EnumSource(ProductCondition.Sort.class)
    @DisplayName("AC-277 店舗の並び順の全ての値を、商品カタログの並び順の同じ名前の値に写す")
    void mapsEverySort(ProductCondition.Sort sort) {
        // 操作と確認：同じ名前の値になる
        assertThat(CatalogApiDirectory.toSort(sort)).isEqualTo(ShownProductSort.valueOf(sort.name()));
    }
}
