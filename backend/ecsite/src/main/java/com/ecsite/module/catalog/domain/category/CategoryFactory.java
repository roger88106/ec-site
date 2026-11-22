package com.ecsite.module.catalog.domain.category;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.base.RestoreGuard;
import com.ecsite.common.model.id.CategoryId;
import org.jmolecules.ddd.annotation.Factory;
import org.jspecify.annotations.Nullable;

/**
 * カテゴリのファクトリー
 */
@Factory
public class CategoryFactory {

    /**
     * カテゴリを作る。
     *
     * @param id           カテゴリ ID
     * @param parent       親カテゴリ。最上位なら null
     * @param name         名称
     * @param displayOrder 表示順
     * @return カテゴリ
     * @throws BusinessException 深さが上限を超える（{@code CATEGORY_DEPTH_EXCEEDED}）
     * @throws BusinessException 名称が範囲の外（{@code INVALID_CATEGORY_NAME}）
     */
    public Category create(CategoryId id, @Nullable Category parent, String name, int displayOrder) {
        return Category.create(id, parent, name, displayOrder);
    }

    /**
     * スナップショットからカテゴリを復元する
     *
     * @param snapshot カテゴリのスナップショット
     * @return カテゴリ
     */
    public Category restore(Category.Snapshot snapshot) {
        // 復元失敗の場合、復元エラーに変換してスローする
        return RestoreGuard.restore("category", () -> new Category(snapshot));
    }
}
