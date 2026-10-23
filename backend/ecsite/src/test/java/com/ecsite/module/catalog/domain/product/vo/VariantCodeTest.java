package com.ecsite.module.catalog.domain.product.vo;

import com.ecsite.module.catalog.domain.ProductErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * バリアント品番の単体テスト（T302）
 */
class VariantCodeTest {

    @ParameterizedTest
    @ValueSource(strings = {"ABC-001-WH-M", "A", "12345678901234567890123456789012"})
    @DisplayName("AC-21 形式は商品品番と同じ。正しい形式なら受け付ける")
    void acceptsValidCode(String value) {
        assertThat(VariantCode.of(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"-WH", "WH-", "WH--M", "wh-m", "123456789012345678901234567890123"})
    @DisplayName("AC-21 形式が違えば拒否される")
    void rejectsInvalidCode(String value) {
        assertBusinessError(() -> VariantCode.of(value), ProductErrorCode.INVALID_VARIANT_CODE);
    }
}
