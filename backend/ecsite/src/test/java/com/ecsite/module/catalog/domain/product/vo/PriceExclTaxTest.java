package com.ecsite.module.catalog.domain.product.vo;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.catalog.domain.product.ProductErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 税抜価格の単体テスト（T302）
 */
class PriceExclTaxTest {

    @Test
    @DisplayName("AC-25 1 円以上なら受け付け、金額として取り出せる")
    void acceptsOneYenOrMore() {
        assertThat(PriceExclTax.of(1).toMoney()).isEqualTo(Money.of(1));
        assertThat(PriceExclTax.of(1_000_000).yen()).isEqualTo(1_000_000);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("AC-25 1 円未満なら拒否される")
    void rejectsLessThanOneYen(long yen) {
        assertBusinessError(() -> PriceExclTax.of(yen), ProductErrorCode.INVALID_PRICE_EXCL_TAX);
    }
}
