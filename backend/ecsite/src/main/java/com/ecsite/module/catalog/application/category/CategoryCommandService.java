package com.ecsite.module.catalog.application.category;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.base.AggregateGuards;
import com.ecsite.module.catalog.application.model.command.CreateCategoryCommand;
import com.ecsite.module.catalog.application.model.command.RenameCategoryCommand;
import com.ecsite.module.catalog.application.model.command.ReorderCategoriesCommand;
import com.ecsite.module.catalog.domain.category.Category;
import com.ecsite.module.catalog.domain.category.CategoryErrorCode;
import com.ecsite.module.catalog.domain.category.CategoryFactory;
import com.ecsite.module.catalog.domain.category.port.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * カテゴリのコマンドサービス
 */
@Service
@RequiredArgsConstructor
public class CategoryCommandService {

    /**
     * カテゴリリポジトリ
     */
    private final CategoryRepository categoryRepository;

    /**
     * カテゴリファクトリ
     */
    private final CategoryFactory categoryFactory;

    /**
     * カテゴリを登録する。兄弟の最後に並べる。
     *
     * @param command カテゴリの登録コマンド
     * @return 登録したカテゴリの ID
     */
    @Transactional
    public UUID createCategory(CreateCategoryCommand command) {
        // 親カテゴリ取得 ※深さは親の深さで決まる
        var parent = command.parentId() == null ? null : getCategory(command.parentId());

        // カテゴリ作成
        int displayOrder = categoryRepository.nextDisplayOrder(command.parentId());
        var category = categoryFactory.create(parent, command.name(), displayOrder);

        // カテゴリ登録 ※名称の重複は DB の一意制約で検出する
        categoryRepository.add(category);

        return category.id();
    }

    /**
     * カテゴリ名を変更する。
     *
     * @param command カテゴリ名の変更コマンド
     */
    @Transactional
    public void renameCategory(RenameCategoryCommand command) {
        // カテゴリ取得
        var category = getCategory(command.categoryId());

        // 取得時のバージョンと一致しなければ、他の人が先に更新している（保存のときにもリポジトリが比べる）
        AggregateGuards.requireVersion(category.version(), command.version());

        // 名称変更
        category.rename(command.name());

        // カテゴリ保存 ※名称の重複は DB の一意制約で検出する
        categoryRepository.save(category);
    }

    /**
     * 兄弟カテゴリの並び順を変更する。
     *
     * @param command 並び順の変更コマンド
     */
    @Transactional
    public void reorderCategories(ReorderCategoriesCommand command) {
        // 親カテゴリの存在確認
        if (command.parentId() != null) {
            getCategory(command.parentId());
        }

        // 兄弟をロックして取得 ※同時の並び順の変更を直列にする
        var siblings = categoryRepository.lockChildren(command.parentId());

        // 並び順変更 ※表示順が変わったカテゴリだけ保存する
        Category.reorder(siblings, command.categoryIds()).forEach(categoryRepository::save);
    }

    /**
     * カテゴリを削除する。
     *
     * @param categoryId カテゴリ ID
     */
    @Transactional
    public void deleteCategory(UUID categoryId) {
        // カテゴリ取得
        var category = getCategory(categoryId);

        // 子カテゴリを持つカテゴリは削除できない ※同時に子が登録された場合は、DB の外部キーで検出する
        if (categoryRepository.hasChildren(categoryId)) {
            throw BusinessException.of(CategoryErrorCode.CATEGORY_HAS_CHILDREN);
        }

        // 商品が所属しているカテゴリは削除できない ※同時に商品へ割り当てられた場合は、DB の外部キーで検出する
        if (categoryRepository.hasProducts(categoryId)) {
            throw BusinessException.of(CategoryErrorCode.CATEGORY_HAS_PRODUCTS);
        }

        // カテゴリ削除
        categoryRepository.delete(category);
    }

// ============================== プライベートメソッド ==============================

    /**
     * カテゴリを取得する。なければ {@code NOT_FOUND} の業務のエラーを投げる。
     *
     * @param id カテゴリ ID
     * @return カテゴリ
     */
    private Category getCategory(UUID id) {
        return categoryRepository.findById(id).orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
    }
}
