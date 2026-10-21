package com.ecsite.module.catalog.domain.category;

import com.ecsite.common.model.base.AggregateGuards;

import java.util.UUID;

/**
 * カテゴリのファクトリー
 */
public class CategoryFactory {

    /**
     * カテゴリを作る。
     *
     * @param parent       親カテゴリ。最上位なら null
     * @param name         名称
     * @param displayOrder 表示順
     * @return カテゴリ
     */
    public Category create(Category parent, String name, int displayOrder) {
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
        return AggregateGuards.restore("category", snapshot, () -> new Category(snapshot));
    }
}
