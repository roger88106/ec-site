package com.ecsite.module.tax.infrastructure;

import com.ecsite.jooq.tax.tables.records.TaxCategoryRecord;
import com.ecsite.jooq.tax.tables.records.TaxRateRecord;
import com.ecsite.module.tax.application.TaxCategoryQueryRepository;
import com.ecsite.module.tax.application.model.view.TaxCategoryView;
import com.ecsite.module.tax.application.model.view.TaxCategoryView.TaxRateView;
import com.ecsite.module.tax.domain.taxcategory.vo.RatePercent;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.ecsite.jooq.tax.Tables.TAX_CATEGORY;
import static com.ecsite.jooq.tax.Tables.TAX_RATE;
import static org.jooq.impl.DSL.noCondition;

/**
 * 税区分のクエリリポジトリ
 */
@Repository
@RequiredArgsConstructor
class JooqTaxCategoryQueryRepository implements TaxCategoryQueryRepository {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * すべての税区分を取得する。
     *
     * @return 税区分の一覧（登録の古い順）
     */
    @Override
    public List<TaxCategoryView> findAll() {
        return find(noCondition());
    }

    /**
     * 税区分を取得する。
     *
     * @param id 税区分 ID
     * @return 税区分
     */
    @Override
    public Optional<TaxCategoryView> findById(UUID id) {
        return find(TAX_CATEGORY.ID.eq(id)).stream().findFirst();
    }

// ============================== プライベートメソッド ==============================

    /**
     * 条件に合う税区分を、税率とあわせて取得する。
     *
     * @param condition 税区分の条件
     * @return 税区分の一覧（登録の古い順。同じなら ID 順）
     */
    private List<TaxCategoryView> find(Condition condition) {
        var categories = dsl.selectFrom(TAX_CATEGORY)
                .where(condition)
                .orderBy(TAX_CATEGORY.CREATED_AT, TAX_CATEGORY.ID)
                .fetch();
        if (categories.isEmpty()) {
            return List.of();
        }

        // 税率（適用開始日時の古い順。空白が先頭）
        Map<UUID, List<TaxRateView>> ratesByCategory = dsl.selectFrom(TAX_RATE)
                .where(TAX_RATE.TAX_CATEGORY_ID.in(categories.map(TaxCategoryRecord::getId)))
                .orderBy(TAX_RATE.EFFECTIVE_FROM.asc().nullsFirst())
                .fetch()
                .stream()
                .collect(Collectors.groupingBy(TaxRateRecord::getTaxCategoryId,
                        Collectors.mapping(JooqTaxCategoryQueryRepository::toView, Collectors.toList())));

        return categories.map(category -> new TaxCategoryView(
                category.getId(),
                category.getName(),
                null,
                ratesByCategory.getOrDefault(category.getId(), List.of()),
                category.getVersion()));
    }

    /**
     * レコードから税率のビューに変換する。
     *
     * @param record 税率のレコード
     * @return 税率のビュー
     */
    private static TaxRateView toView(TaxRateRecord record) {
        return new TaxRateView(
                record.getId(),
                // numeric(4,2) の末尾の 0 を除いた形にそろえる（10.00 → 10）
                RatePercent.of(record.getRatePercent()).value(),
                record.getEffectiveFrom(),
                record.getEffectiveTo());
    }
}
