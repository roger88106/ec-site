package com.ecsite.module.catalog.presentation;

import com.ecsite.api.admin.CategoryApi;
import com.ecsite.api.admin.model.Category;
import com.ecsite.api.admin.model.CategoryTree;
import com.ecsite.api.admin.model.CreateCategoryRequest;
import com.ecsite.api.admin.model.CreatedId;
import com.ecsite.api.admin.model.RenameCategoryRequest;
import com.ecsite.api.admin.model.ReorderCategoriesRequest;
import com.ecsite.common.presentation.CreatedResponses;
import com.ecsite.module.catalog.application.category.CategoryCommandService;
import com.ecsite.module.catalog.application.category.CategoryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * カテゴリのコントローラー（B 側。admin.yaml の tag: category）
 */
@RestController
@RequiredArgsConstructor
class CategoryController implements CategoryApi {

    /**
     * カテゴリコマンドサービス
     */
    private final CategoryCommandService commandService;

    /**
     * カテゴリクエリサービス
     */
    private final CategoryQueryService queryService;

    /**
     * カテゴリマッパー
     */
    private final CategoryMapper mapper;

// ============================== GET ==============================

    /**
     * カテゴリの木
     *
     * @return Http 200 : {@link CategoryTree}
     */
    @Override
    public ResponseEntity<CategoryTree> listCategories() {
        return ResponseEntity.ok(mapper.toCategoryTree(queryService.getCategoryTree()));
    }

    /**
     * カテゴリの取得
     *
     * @param categoryId カテゴリ ID
     * @return Http 200 : {@link Category}
     */
    @Override
    public ResponseEntity<Category> getCategory(UUID categoryId) {
        return ResponseEntity.ok(mapper.toCategory(queryService.getCategory(categoryId)));
    }

// ============================== POST ==============================

    /**
     * カテゴリの登録
     *
     * @param request カテゴリの登録リクエスト
     * @return Http 201 : {@link CreatedId}
     */
    @Override
    public ResponseEntity<CreatedId> createCategory(CreateCategoryRequest request) {
        // カテゴリ登録
        UUID id = commandService.createCategory(mapper.toCreateCategoryCommand(request));

        // ロケーション：登録したカテゴリの URL
        return CreatedResponses.admin(id);
    }

// ============================== PUT ==============================

    /**
     * カテゴリ名の変更
     *
     * @param categoryId カテゴリ ID
     * @param request    カテゴリ名の変更リクエスト
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> renameCategory(UUID categoryId, RenameCategoryRequest request) {
        commandService.renameCategory(mapper.toRenameCategoryCommand(categoryId, request));
        return ResponseEntity.noContent().build();
    }

    /**
     * 兄弟カテゴリの並び順の変更
     *
     * @param request 並び順の変更リクエスト
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> reorderCategories(ReorderCategoriesRequest request) {
        commandService.reorderCategories(mapper.toReorderCategoriesCommand(request));
        return ResponseEntity.noContent().build();
    }

// ============================== DELETE ==============================

    /**
     * カテゴリの削除
     *
     * @param categoryId カテゴリ ID
     * @return Http 204 : VOID
     */
    @Override
    public ResponseEntity<Void> deleteCategory(UUID categoryId) {
        commandService.deleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }
}
