package com.ecsite.module.reporting.infrastructure.persistence;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.reporting.application.port.ReportQueryRepository;
import com.ecsite.module.reporting.domain.Basis;
import com.ecsite.module.reporting.domain.DailySales;
import com.ecsite.module.reporting.domain.ReportBuyerGender;
import com.ecsite.module.reporting.domain.ReportPeriod;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.jooq.reporting.Tables.SALES_ORDER;
import static com.ecsite.jooq.reporting.Tables.SALES_ORDER_LINE;

/**
 * 写しを集計する問い合わせの jOOQ の実装（docs/design/modules/reporting.md「集計」）
 */
@Repository
@RequiredArgsConstructor
class JooqReportQueryRepository implements ReportQueryRepository {

    /**
     * jOOQ DSL Context
     */
    private final DSLContext dsl;

    /**
     * 日ごとの売上額と販売数量を求める。
     *
     * @param period          期間
     * @param basis           売上の数え方
     * @param productId       商品 ID。全商品なら null
     * @param excludeTax      消費税を除くか
     * @param excludeShipping 送料を除くか（商品を指定したときは使わない）
     * @return 日 → 売上。売れなかった日は含めない
     */
    @Override
    public Map<LocalDate, DailySales> dailySales(ReportPeriod period, Basis basis, @Nullable UUID productId, boolean excludeTax,
                                                 boolean excludeShipping) {
        var day = dayOf(basis);
        var result = new LinkedHashMap<LocalDate, DailySales>();
        if (productId == null) {
            // 注文ごとの販売数量は、明細の数量の合計
            Field<BigDecimal> quantity = DSL.field(DSL.select(DSL.sum(SALES_ORDER_LINE.QUANTITY))
                    .from(SALES_ORDER_LINE)
                    .where(SALES_ORDER_LINE.ORDER_ID.eq(SALES_ORDER.ORDER_ID)));
            dsl.select(day, DSL.sum(orderAmount(excludeTax, excludeShipping)), DSL.sum(quantity))
                    .from(SALES_ORDER)
                    .where(counted(period, basis))
                    .groupBy(day)
                    .forEach(row -> result.put(row.value1(), new DailySales(money(row.value2()), count(row.value3()))));
        } else {
            dsl.select(day, DSL.sum(lineAmount(excludeTax)), DSL.sum(SALES_ORDER_LINE.QUANTITY))
                    .from(SALES_ORDER)
                    .join(SALES_ORDER_LINE).on(SALES_ORDER_LINE.ORDER_ID.eq(SALES_ORDER.ORDER_ID))
                    .where(counted(period, basis), SALES_ORDER_LINE.PRODUCT_ID.eq(productId))
                    .groupBy(day)
                    .forEach(row -> result.put(row.value1(), new DailySales(money(row.value2()), count(row.value3()))));
        }
        return result;
    }

    /**
     * 商品のバリアントごとの販売数量と売上額を求める（送料は含めない）。
     *
     * @param period     期間
     * @param basis      売上の数え方
     * @param productId  商品 ID
     * @param excludeTax 消費税を除くか
     * @return 売れたバリアントの売上
     */
    @Override
    public List<VariantTotal> variantSales(ReportPeriod period, Basis basis, UUID productId, boolean excludeTax) {
        return dsl.select(SALES_ORDER_LINE.VARIANT_ID, DSL.max(SALES_ORDER_LINE.VARIANT_CODE),
                        DSL.sum(SALES_ORDER_LINE.QUANTITY), DSL.sum(lineAmount(excludeTax)))
                .from(SALES_ORDER)
                .join(SALES_ORDER_LINE).on(SALES_ORDER_LINE.ORDER_ID.eq(SALES_ORDER.ORDER_ID))
                .where(counted(period, basis), SALES_ORDER_LINE.PRODUCT_ID.eq(productId))
                .groupBy(SALES_ORDER_LINE.VARIANT_ID)
                .fetch(row -> new VariantTotal(row.value1(), row.value2(), count(row.value3()), money(row.value4())));
    }

    /**
     * 都道府県・年齢・性別の組ごとの売上額と注文件数を求める。
     *
     * @param period          期間
     * @param basis           売上の数え方
     * @param productId       商品 ID。全商品なら null
     * @param excludeTax      消費税を除くか
     * @param excludeShipping 送料を除くか（商品を指定したときは使わない）
     * @return 組ごとの売上
     */
    @Override
    public List<SegmentTotal> segmentSales(ReportPeriod period, Basis basis, @Nullable UUID productId, boolean excludeTax,
                                           boolean excludeShipping) {
        var keys = List.<Field<?>>of(SALES_ORDER.PREFECTURE_CODE, SALES_ORDER.BUYER_AGE, SALES_ORDER.BUYER_GENDER);
        if (productId == null) {
            return dsl.select(SALES_ORDER.PREFECTURE_CODE, SALES_ORDER.BUYER_AGE, SALES_ORDER.BUYER_GENDER,
                            DSL.sum(orderAmount(excludeTax, excludeShipping)), DSL.count())
                    .from(SALES_ORDER)
                    .where(counted(period, basis))
                    .groupBy(keys)
                    .fetch(row -> new SegmentTotal(row.value1(), age(row.value2()), buyerGender(row.value3()), money(row.value4()),
                            row.value5()));
        }
        // 商品を選んだら、その商品の明細を含む注文だけを数え、売上額はその明細の分だけ
        return dsl.select(SALES_ORDER.PREFECTURE_CODE, SALES_ORDER.BUYER_AGE, SALES_ORDER.BUYER_GENDER,
                        DSL.sum(lineAmount(excludeTax)), DSL.countDistinct(SALES_ORDER.ORDER_ID))
                .from(SALES_ORDER)
                .join(SALES_ORDER_LINE).on(SALES_ORDER_LINE.ORDER_ID.eq(SALES_ORDER.ORDER_ID))
                .where(counted(period, basis), SALES_ORDER_LINE.PRODUCT_ID.eq(productId))
                .groupBy(keys)
                .fetch(row -> new SegmentTotal(row.value1(), age(row.value2()), buyerGender(row.value3()), money(row.value4()),
                        row.value5()));
    }

// ============================== プライベートメソッド ==============================

    /**
     * 列の性別の値を、注文時の性別の列挙にする。
     */
    private static @Nullable ReportBuyerGender buyerGender(@Nullable String value) {
        return value == null ? null : ReportBuyerGender.valueOf(value);
    }

    /**
     * 数える日：注文基準は注文日、出荷基準は出荷日
     */
    private static Field<LocalDate> dayOf(Basis basis) {
        return basis == Basis.ORDER ? SALES_ORDER.ORDERED_ON : SALES_ORDER.SHIPPED_ON;
    }

    /**
     * 数える注文：売上の数え方の状態で、数える日が期間に入るもの
     */
    private static Condition counted(ReportPeriod period, Basis basis) {
        return SALES_ORDER.STATUS.in(basis.countedStatuses().stream().map(Enum::name).toList())
                .and(dayOf(basis).between(period.from(), period.to()));
    }

    /**
     * 注文の売上額：総額から、消費税を除くなら消費税の合計を、送料を除くなら送料（消費税を除くなら税抜の送料）を引く
     */
    private static Field<Long> orderAmount(boolean excludeTax, boolean excludeShipping) {
        Field<Long> amount = SALES_ORDER.TOTAL_AMOUNT;
        if (excludeTax) {
            amount = amount.minus(SALES_ORDER.TAX_TOTAL);
        }
        if (excludeShipping) {
            amount = amount.minus(excludeTax ? SALES_ORDER.SHIPPING_FEE.minus(SALES_ORDER.SHIPPING_TAX) : SALES_ORDER.SHIPPING_FEE);
        }
        return amount;
    }

    /**
     * 明細の売上額：税込小計か、消費税を除くなら税抜小計
     */
    private static Field<Long> lineAmount(boolean excludeTax) {
        return excludeTax ? SALES_ORDER_LINE.SUBTOTAL_EXCL_TAX : SALES_ORDER_LINE.SUBTOTAL_INCL_TAX;
    }

    /**
     * 合計の金額（行がなければ 0）
     */
    private static Money money(BigDecimal sum) {
        return sum == null ? Money.ZERO : Money.of(sum.longValueExact());
    }

    /**
     * 年齢（記録がなければ null）
     */
    private static @Nullable Integer age(@Nullable Short age) {
        return age == null ? null : age.intValue();
    }

    /**
     * 合計の数（行がなければ 0）
     */
    private static long count(BigDecimal sum) {
        return sum == null ? 0 : sum.longValueExact();
    }
}
