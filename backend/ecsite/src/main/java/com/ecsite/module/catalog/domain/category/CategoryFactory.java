package com.ecsite.module.catalog.domain.category;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.base.AggregateGuards;
import org.jmolecules.ddd.annotation.Factory;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * カテゴリのファクトリー
 */
@Factory
public class CategoryFactory {

    /**
     * カテゴリを作る。
     *
     * @param parent       親カテゴリ。最上位なら null
     * @param name         名称
     * @param displayOrder 表示順
     * @return カテゴリ
     * @throws BusinessException 深さが上限を超える（{@code CATEGORY_DEPTH_EXCEEDED}）。名称が範囲の外（{@code INVALID_CATEGORY_NAME}）
     */
    public Category create(@Nullable Category parent, String name, int displayOrder) {
        return Category.create(UUID.randomUUID(), parent, name, displayOrder);
    }

    /**
     * スナップショットからカテゴリを復元する
     *
     * @param snapshot カテゴリのスナップショット
     * @return カテゴリ
     */
    public Category restore(Category.Snapshot snapshot) {
        // 復元失敗の場合、復元エラーに変換してスローする
        return AggregateGuards.restore("category", () -> new Category(snapshot));
    }
}
