package com.ecsite.module.catalog.domain.category.port;

import com.ecsite.module.catalog.domain.category.Category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * カテゴリのリポジトリ
 */
public interface CategoryRepository {

    /**
     * カテゴリを取得する。
     *
     * @param id カテゴリ ID
     * @return カテゴリ。なければ空
     */
    Optional<Category> findById(UUID id);

    /**
     * 子カテゴリをすべて取得し、トランザクションの終わりまでロックする。<br />
     * 並び順の同時の変更を直列にするため、ID の順にロックする（docs/modules/catalog.md）。
     *
     * @param parentId 親カテゴリ ID。最上位なら null
     * @return 子カテゴリの一覧
     */
    List<Category> lockChildren(UUID parentId);

    /**
     * 新しい子カテゴリの表示順（兄弟の最大＋1。兄弟がなければ 1）を返す。
     *
     * @param parentId 親カテゴリ ID。最上位なら null
     * @return 表示順
     */
    int nextDisplayOrder(UUID parentId);

    /**
     * 子カテゴリを持つかを返す。
     *
     * @param id カテゴリ ID
     * @return 子カテゴリを持つなら true
     */
    boolean hasChildren(UUID id);

    /**
     * カテゴリを登録する。<br />
     * 同じ親の下で名称が重複していれば {@code CATEGORY_NAME_ALREADY_EXISTS}、
     * 親が削除されていれば {@code NOT_FOUND} の業務のエラーを投げる。
     *
     * @param category カテゴリ
     */
    void add(Category category);

    /**
     * カテゴリを更新する。<br />
     * 取得後に他で更新されていれば {@code CONCURRENT_MODIFICATION}、
     * 同じ親の下で名称が重複していれば {@code CATEGORY_NAME_ALREADY_EXISTS} の業務のエラーを投げる。
     *
     * @param category カテゴリ
     */
    void save(Category category);

    /**
     * カテゴリを削除する。<br />
     * 子カテゴリを持てば {@code CATEGORY_HAS_CHILDREN}、すでに削除されていれば {@code NOT_FOUND} の業務のエラーを投げる。
     *
     * @param category カテゴリ
     */
    void delete(Category category);
}
