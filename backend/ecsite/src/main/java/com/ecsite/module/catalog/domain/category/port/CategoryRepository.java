package com.ecsite.module.catalog.domain.category.port;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.domain.category.Category;
import org.jmolecules.ddd.annotation.Repository;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * カテゴリのリポジトリ
 */
@Repository
public interface CategoryRepository {

    /**
     * カテゴリを取得する。
     *
     * @param id カテゴリ ID
     * @return カテゴリ。なければ空
     */
    Optional<Category> findById(UUID id);

    /**
     * 子カテゴリを全て取得し、ID の順にロックする。
     *
     * <p>ロックはトランザクションの終わりまで続く。
     *
     * @param parentId 親カテゴリ ID。最上位なら null
     * @return 子カテゴリの一覧
     */
    List<Category> lockChildren(@Nullable UUID parentId);

    /**
     * 新しい子カテゴリの表示順（兄弟の最大＋1。兄弟がなければ 1）を返す。
     *
     * @param parentId 親カテゴリ ID。最上位なら null
     * @return 表示順
     */
    int nextDisplayOrder(@Nullable UUID parentId);

    /**
     * 子カテゴリを持つかを返す。
     *
     * @param id カテゴリ ID
     * @return 子カテゴリを持つなら true
     */
    boolean hasChildren(UUID id);

    /**
     * 商品が所属しているかを返す。
     *
     * @param id カテゴリ ID
     * @return 商品が所属していれば true
     */
    boolean hasProducts(UUID id);

    /**
     * カテゴリが全て存在するかを返す。
     *
     * @param ids カテゴリ ID
     * @return 全て存在すれば true。空なら true
     */
    boolean existsAll(Collection<UUID> ids);

    /**
     * カテゴリを登録する。
     *
     * @param category カテゴリ
     * @throws BusinessException 同じ親の下で名称が重複している（{@code CATEGORY_NAME_ALREADY_EXISTS}）
     * @throws BusinessException 親が削除されている（{@code NOT_FOUND}）
     */
    void add(Category category);

    /**
     * カテゴリを更新する。
     *
     * @param category カテゴリ
     * @throws BusinessException 取得した後に他で更新された（{@code CONCURRENT_MODIFICATION}）
     * @throws BusinessException 同じ親の下で名称が重複している（{@code CATEGORY_NAME_ALREADY_EXISTS}）
     */
    void save(Category category);

    /**
     * 複数のカテゴリを、まとめて更新する。
     *
     * @param categories カテゴリ
     * @throws BusinessException どれかを取得した後に他で更新された（{@code CONCURRENT_MODIFICATION}）
     * @throws BusinessException 同じ親の下で名称が重複している（{@code CATEGORY_NAME_ALREADY_EXISTS}）
     */
    void saveAll(Collection<Category> categories);

    /**
     * カテゴリを削除する。
     *
     * @param category カテゴリ
     * @throws BusinessException 子カテゴリを持つ（{@code CATEGORY_HAS_CHILDREN}）
     * @throws BusinessException 商品が所属している（{@code CATEGORY_HAS_PRODUCTS}）
     * @throws BusinessException 既に削除されている（{@code NOT_FOUND}）
     */
    void delete(Category category);
}
