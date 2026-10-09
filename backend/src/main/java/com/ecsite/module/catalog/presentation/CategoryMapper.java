package com.ecsite.module.catalog.presentation;

import com.ecsite.api.admin.model.Category;
import com.ecsite.api.admin.model.CategoryNode;
import com.ecsite.api.admin.model.CreateCategoryRequest;
import com.ecsite.api.admin.model.RenameCategoryRequest;
import com.ecsite.api.admin.model.ReorderCategoriesRequest;
import com.ecsite.module.catalog.application.model.command.CreateCategoryCommand;
import com.ecsite.module.catalog.application.model.command.RenameCategoryCommand;
import com.ecsite.module.catalog.application.model.command.ReorderCategoriesCommand;
import com.ecsite.module.catalog.application.model.view.CategoryNodeView;
import com.ecsite.module.catalog.application.model.view.CategoryView;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * カテゴリ API のリクエスト・レスポンスと、アプリケーション層のモデルの変換
 */
@Component
class CategoryMapper {

    /**
     * カテゴリの登録コマンドに変換する。
     *
     * @param request カテゴリの登録リクエスト
     * @return カテゴリの登録コマンド
     */
    CreateCategoryCommand toCreateCategoryCommand(CreateCategoryRequest request) {
        return new CreateCategoryCommand(request.getName(), request.getParentId().orElse(null));
    }

    /**
     * カテゴリ名の変更コマンドに変換する。
     *
     * @param categoryId カテゴリ ID
     * @param request    カテゴリ名の変更リクエスト
     * @return カテゴリ名の変更コマンド
     */
    RenameCategoryCommand toRenameCategoryCommand(UUID categoryId, RenameCategoryRequest request) {
        return new RenameCategoryCommand(categoryId, request.getName(), request.getVersion());
    }

    /**
     * 並び順の変更コマンドに変換する。
     *
     * @param request 並び順の変更リクエスト
     * @return 並び順の変更コマンド
     */
    ReorderCategoriesCommand toReorderCategoriesCommand(ReorderCategoriesRequest request) {
        // categoryIds は LinkedHashSet で受け取るので、送られた順を保つ
        return new ReorderCategoriesCommand(request.getParentId().orElse(null), List.copyOf(request.getCategoryIds()));
    }

    /**
     * カテゴリのレスポンスに変換する。
     *
     * @param view カテゴリのビュー
     * @return カテゴリのレスポンス
     */
    Category toCategory(CategoryView view) {
        return new Category(view.id(), view.name(), view.depth(), view.displayOrder(), view.version())
                .parentId(view.parentId());
    }

    /**
     * カテゴリの木の節のレスポンスに変換する。
     *
     * @param view カテゴリの木の節のビュー
     * @return カテゴリの木の節のレスポンス
     */
    CategoryNode toCategoryNode(CategoryNodeView view) {
        return new CategoryNode(
                view.id(),
                view.name(),
                view.depth(),
                view.displayOrder(),
                view.version(),
                view.children().stream().map(this::toCategoryNode).toList());
    }
}
