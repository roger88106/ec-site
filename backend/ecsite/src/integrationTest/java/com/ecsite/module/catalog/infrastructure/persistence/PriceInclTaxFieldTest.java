package com.ecsite.module.catalog.infrastructure.persistence;

import com.ecsite.IntegrationTest;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.tax.domain.taxcategory.TaxCalculator;
import com.ecsite.module.tax.domain.taxcategory.vo.RatePercent;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 店舗画面の一覧・検索の税込価格（SQL の式）と、税モジュールの計算（Java）が同じ結果になることの検証。<br />
 * 税込価格の計算は 2 か所にあるので、片方だけを変えると、一覧と詳細・カートで価格が食い違う。
 */
@IntegrationTest
@Transactional
class PriceInclTaxFieldTest {

    @Autowired
    DSLContext dsl;

    @ParameterizedTest(name = "税抜 {0} 円、率 {1}%")
    @CsvSource({
            "1, 8", "1, 10", "99, 8", "99, 10", "999, 8", "999, 10", "12345, 8",
            "1000, 10", "1000, 0", "1, 0",
    })
    @DisplayName("税込価格の SQL の式は、税モジュールの計算と同じ結果になる（1 円未満を切り捨てる）")
    void matchesTaxCalculator(long priceExclTax, BigDecimal ratePercent) {
        var taxCategoryId = UUID.randomUUID();
        var productId = insertProduct(taxCategoryId, priceExclTax);

        var sql = dsl.select(PriceInclTaxField.of(Map.of(taxCategoryId, ratePercent)))
                .from(PRODUCT).where(PRODUCT.ID.eq(productId))
                .fetchOne().value1();

        assertThat(sql).isEqualTo(TaxCalculator.priceInclTax(Money.of(priceExclTax), RatePercent.of(ratePercent)).yen());
    }

    /**
     * 税区分と税抜価格だけを決めた商品を、DB に直接入れる（式が商品の表の列を読むため）。
     */
    private UUID insertProduct(UUID taxCategoryId, long priceExclTax) {
        var id = UUID.randomUUID();
        var now = Instant.now();
        dsl.insertInto(PRODUCT)
                .set(PRODUCT.ID, id)
                .set(PRODUCT.PRODUCT_CODE, "PIT-" + id.toString().substring(0, 8))
                .set(PRODUCT.NAME, "税込価格の確かめ")
                .set(PRODUCT.DESCRIPTION, "")
                .set(PRODUCT.TAX_CATEGORY_ID, taxCategoryId)
                .set(PRODUCT.PRICE_EXCL_TAX, priceExclTax)
                .set(PRODUCT.STATUS, "UNPUBLISHED")
                .set(PRODUCT.VERSION, 0L)
                .set(PRODUCT.CREATED_AT, now)
                .set(PRODUCT.UPDATED_AT, now)
                .execute();
        return id;
    }
}
