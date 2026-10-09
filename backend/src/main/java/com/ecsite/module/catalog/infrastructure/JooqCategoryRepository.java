package com.ecsite.module.catalog.infrastructure;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.persistence.ConstraintViolationTranslator;
import com.ecsite.jooq.catalog.Keys;
import com.ecsite.jooq.catalog.tables.records.CategoryRecord;
import com.ecsite.module.catalog.domain.category.Category;
import com.ecsite.module.catalog.domain.category.CategoryErrorCode;
import com.ecsite.module.catalog.domain.category.CategoryFactory;
import com.ecsite.module.catalog.domain.category.port.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.ecsite.jooq.catalog.Tables.CATEGORY;

/**
 * カテゴリのリポジトリ
 */
@Repository
@RequiredArgsConstructor
class JooqCategoryRepository implements CategoryRepository {

    /**
     * 同じ親の下での名称の重複
     */
    private static final String NAME_KEY = Keys.CATEGORY_PARENT_ID_NAME_KEY.getName();

    /**
     * 親カテゴリへの外部キー
     */
    private static final String PARENT_FKEY = Keys.CATEGORY__CATEGORY_PARENT_ID_FKEY.getName();

    /**
     * 登録・更新時の制約名 → 業務のエラー。登録時の外部キーの違反は、親が同時に削除された場合
     */
    private static final ConstraintViolationTranslator WRITE_CONSTRAINTS = ConstraintViolationTranslator.of(Map.of(
            NAME_KEY, () -> BusinessException.of(CategoryErrorCode.CATEGORY_NAME_ALREADY_EXISTS),
            PARENT_FKEY, () -> BusinessException.of(CommonErrorCode.NOT_FOUND)));

    /**
     * 削除時の制約名 → 業務のエラー。外部キーの違反は、子カテゴリが同時に登録された場合
     */
    private static final ConstraintViolationTranslator DELETE_CONSTRAINTS = ConstraintViolationTranslator.of(Map.of(
            PARENT_FKEY, () -> BusinessException.of(CategoryErrorCode.CATEGORY_HAS_CHILDREN)));

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * Spring イベントパブリッシャー
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * カテゴリファクトリ
     */
    private final CategoryFactory factory;

    /**
     * 時計（登録・更新日時）
     */
    private final Clock clock;

    /**
     * カテゴリを取得する。
     *
     * @param id カテゴリ ID
     * @return カテゴリ
     */
    @Override
    public Optional<Category> findById(UUID id) {
        return dsl.selectFrom(CATEGORY)
                .where(CATEGORY.ID.eq(id))
                .fetchOptional()
                .map(this::toCategory);
    }

    /**
     * 子カテゴリをすべて取得し、ID の順にロックする。
     *
     * @param parentId 親カテゴリ ID。最上位なら null
     * @return 子カテゴリの一覧
     */
    @Override
    public List<Category> lockChildren(UUID parentId) {
        return dsl.selectFrom(CATEGORY)
                .where(childOf(parentId))
                .orderBy(CATEGORY.ID)
                .forUpdate()
                .fetch(this::toCategory);
    }

    /**
     * 新しい子カテゴリの表示順を返す。
     *
     * @param parentId 親カテゴリ ID。最上位なら null
     * @return 兄弟の最大＋1。兄弟がなければ 1
     */
    @Override
    public int nextDisplayOrder(UUID parentId) {
        return dsl.select(DSL.coalesce(DSL.max(CATEGORY.DISPLAY_ORDER), 0).plus(1))
                .from(CATEGORY)
                .where(childOf(parentId))
                .fetchSingle()
                .value1();
    }

    /**
     * 子カテゴリを持つかを返す。
     *
     * @param id カテゴリ ID
     * @return 子カテゴリを持つなら true
     */
    @Override
    public boolean hasChildren(UUID id) {
        return dsl.fetchExists(CATEGORY, CATEGORY.PARENT_ID.eq(id));
    }

    /**
     * カテゴリを登録する。
     *
     * @param category カテゴリ
     */
    @Override
    public void add(Category category) {
        var snapshot = category.toSnapshot();
        var now = clock.instant();

        try {
            dsl.insertInto(CATEGORY)
                    .set(CATEGORY.ID, snapshot.id())
                    .set(CATEGORY.PARENT_ID, snapshot.parentId())
                    .set(CATEGORY.NAME, snapshot.name())
                    .set(CATEGORY.DEPTH, (short) snapshot.depth())
                    .set(CATEGORY.DISPLAY_ORDER, snapshot.displayOrder())
                    .set(CATEGORY.VERSION, snapshot.version())
                    .set(CATEGORY.CREATED_AT, now)
                    .set(CATEGORY.UPDATED_AT, now)
                    .execute();
        } catch (DataAccessException | org.jooq.exception.DataAccessException e) {
            throw WRITE_CONSTRAINTS.translate(e);
        }

        // イベント発行
        category.pullEvents().forEach(eventPublisher::publishEvent);
    }

    /**
     * カテゴリを更新する。親と深さは変わらないので、名称と表示順だけを書き換える。
     *
     * @param category カテゴリ
     */
    @Override
    public void save(Category category) {
        var snapshot = category.toSnapshot();

        int affected;
        try {
            // バージョンが一致しなければ、取得後に他で更新されている
            affected = dsl.update(CATEGORY)
                    .set(CATEGORY.NAME, snapshot.name())
                    .set(CATEGORY.DISPLAY_ORDER, snapshot.displayOrder())
                    .set(CATEGORY.VERSION, snapshot.version() + 1)
                    .set(CATEGORY.UPDATED_AT, clock.instant())
                    .where(CATEGORY.ID.eq(snapshot.id()))
                    .and(CATEGORY.VERSION.eq(snapshot.version()))
                    .execute();
        } catch (DataAccessException | org.jooq.exception.DataAccessException e) {
            throw WRITE_CONSTRAINTS.translate(e);
        }
        if (affected == 0) {
            throw BusinessException.of(CommonErrorCode.CONCURRENT_MODIFICATION);
        }

        // イベント発行
        category.pullEvents().forEach(eventPublisher::publishEvent);
    }

    /**
     * カテゴリを削除する。
     *
     * @param category カテゴリ
     */
    @Override
    public void delete(Category category) {
        int affected;
        try {
            affected = dsl.deleteFrom(CATEGORY)
                    .where(CATEGORY.ID.eq(category.id()))
                    .execute();
        } catch (DataAccessException | org.jooq.exception.DataAccessException e) {
            throw DELETE_CONSTRAINTS.translate(e);
        }
        if (affected == 0) {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND);
        }

        // イベント発行
        category.pullEvents().forEach(eventPublisher::publishEvent);
    }

// ============================== プライベートメソッド ==============================

    /**
     * 親カテゴリの子であることの条件。
     *
     * @param parentId 親カテゴリ ID。最上位なら null
     * @return 条件
     */
    private static Condition childOf(UUID parentId) {
        return parentId == null ? CATEGORY.PARENT_ID.isNull() : CATEGORY.PARENT_ID.eq(parentId);
    }

    /**
     * レコードからカテゴリに変換する。
     *
     * @param record カテゴリのレコード
     * @return カテゴリ
     */
    private Category toCategory(CategoryRecord record) {
        return factory.restore(new Category.Snapshot(
                record.getId(),
                record.getParentId(),
                record.getName(),
                record.getDepth(),
                record.getDisplayOrder(),
                record.getVersion()));
    }
}
