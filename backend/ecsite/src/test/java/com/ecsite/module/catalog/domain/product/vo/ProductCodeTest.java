package com.ecsite.module.catalog.domain.product.vo;

import com.ecsite.module.catalog.domain.ProductErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商品品番の単体テスト
 */
class ProductCodeTest {

    @ParameterizedTest
    @ValueSource(strings = {"ABC-001", "A", "0", "ABC", "A-B-C", "12345678901234567890123456789012"})
    @DisplayName("AC-15 英大文字・数字と、英数字の間のハイフンで 1〜32 文字なら受け付ける")
    void acceptsValidCode(String value) {
        assertThat(ProductCode.of(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"-ABC", "ABC-", "ABC--1", "abc-001", "Abc", "ABC 001", "ABC_001", "ＡＢＣ", "123456789012345678901234567890123"})
    @DisplayName("AC-15 形式が違えば拒否される（先頭・末尾・連続のハイフン、英小文字、33 文字以上など）")
    void rejectsInvalidCode(String value) {
        assertBusinessError(() -> ProductCode.of(value), ProductErrorCode.INVALID_PRODUCT_CODE);
    }

    @Test
    @DisplayName("値が同じなら等しい")
    void equalsByValue() {
        assertThat(ProductCode.of("ABC-001")).isEqualTo(ProductCode.of("ABC-001"));
    }
}
