package com.ecsite.module.catalog.domain.category;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.SystemException;

import java.util.UUID;

/**
 * カテゴリのファクトリー
 */
public class CategoryFactory {

    /**
     * カテゴリを作る（AC-8、9）。
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
        try {
            return new Category(snapshot);
        } catch (Exception e) {
            // 復元失敗の場合、復元エラーに変換してスローする
            String debugMessage = """
                    category restore error.
                    snapshot: %s
                    """.formatted(snapshot);
            throw SystemException.of(debugMessage, e, CommonErrorCode.RESTORE_ERROR);
        }
    }
}
