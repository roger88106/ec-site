package com.ecsite.module.catalog.domain.product.vo;

import com.ecsite.module.catalog.domain.ProductErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * バリアント品番の単体テスト
 *
 * <p>形式は商品品番と同じなので、{@code ProductCodeTest} と同じ値で確認する。
 */
class VariantCodeTest {

    @ParameterizedTest(name = "「{0}」")
    @ValueSource(strings = {"ABC-001-WH-M", "A", "0", "ABC", "A-B-C", "12345678901234567890123456789012"})
    @DisplayName("AC-21 形式は商品品番と同じ。正しい形式なら受け付ける")
    void acceptsValidCode(String value) {
        // 操作と確認：受け付け、同じ値を持つ
        assertThat(VariantCode.of(value).value()).isEqualTo(value);
    }

    @ParameterizedTest(name = "「{0}」")
    @NullAndEmptySource
    @ValueSource(strings = {"-WH", "WH-", "WH--M", "wh-m", "Abc", "ABC 001", "ABC_001", "ＡＢＣ", "123456789012345678901234567890123"})
    @DisplayName("AC-21 形式が違えば拒否される")
    void rejectsInvalidCode(String value) {
        // 操作と確認：業務のエラー INVALID_VARIANT_CODE で拒否される
        assertBusinessError(() -> VariantCode.of(value), ProductErrorCode.INVALID_VARIANT_CODE);
    }
}
