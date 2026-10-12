package com.ecsite.module.catalog.infrastructure.persistence;

import com.ecsite.jooq.catalog.tables.records.CategoryRecord;
import com.ecsite.module.catalog.application.model.view.CategoryView;
import com.ecsite.module.catalog.application.port.CategoryQueryRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.ecsite.jooq.catalog.Tables.CATEGORY;

/**
 * カテゴリのクエリリポジトリ
 */
@Repository
@RequiredArgsConstructor
class JooqCategoryQueryRepository implements CategoryQueryRepository {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * すべてのカテゴリを取得する。<br />
     * 同時の登録で表示順が同じになった兄弟は、登録の古い順、ID の順に並べる（docs/modules/catalog.md）。
     *
     * @return カテゴリの一覧
     */
    @Override
    public List<CategoryView> findAll() {
        return dsl.selectFrom(CATEGORY)
                .orderBy(CATEGORY.DISPLAY_ORDER, CATEGORY.CREATED_AT, CATEGORY.ID)
                .fetch(JooqCategoryQueryRepository::toView);
    }

    /**
     * カテゴリを取得する。
     *
     * @param id カテゴリ ID
     * @return カテゴリ
     */
    @Override
    public Optional<CategoryView> findById(UUID id) {
        return dsl.selectFrom(CATEGORY)
                .where(CATEGORY.ID.eq(id))
                .fetchOptional(JooqCategoryQueryRepository::toView);
    }

// ============================== プライベートメソッド ==============================

    /**
     * レコードからビューに変換する。
     *
     * @param record カテゴリのレコード
     * @return カテゴリのビュー
     */
    private static CategoryView toView(CategoryRecord record) {
        return new CategoryView(
                record.getId(),
                record.getParentId(),
                record.getName(),
                record.getDepth(),
                record.getDisplayOrder(),
                record.getVersion());
    }
}
