package com.ecsite.module.catalog.application.port;

import com.ecsite.module.catalog.application.model.view.CategoryView;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * カテゴリのクエリリポジトリ
 */
public interface CategoryQueryRepository {

    /**
     * 全てのカテゴリを取得する。
     *
     * @return カテゴリの一覧（表示順、登録日時の古い順、ID の順）
     */
    List<CategoryView> findAll();

    /**
     * カテゴリを取得する。
     *
     * @param id カテゴリ ID
     * @return カテゴリ。なければ空
     */
    Optional<CategoryView> findById(UUID id);
}
