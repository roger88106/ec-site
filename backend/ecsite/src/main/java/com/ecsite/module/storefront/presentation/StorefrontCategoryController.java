package com.ecsite.module.storefront.presentation;

import com.ecsite.api.store.CategoryApi;
import com.ecsite.api.store.model.CategoryTree;
import com.ecsite.module.storefront.application.StorefrontService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店舗のカテゴリのコントローラー（C 側。store.yaml の tag: category）
 */
@RestController
@RequiredArgsConstructor
class StorefrontCategoryController implements CategoryApi {

    /**
     * 店舗のサービス
     */
    private final StorefrontService storefrontService;

    /**
     * 店舗 API のマッパー
     */
    private final StorefrontMapper mapper;

    /**
     * カテゴリの木（の絞り込み用）
     *
     * @return Http 200 : {@link CategoryTree}
     */
    @Override
    public ResponseEntity<CategoryTree> listCategories() {
        return ResponseEntity.ok(mapper.toCategoryTree(storefrontService.categoryTree()));
    }
}
