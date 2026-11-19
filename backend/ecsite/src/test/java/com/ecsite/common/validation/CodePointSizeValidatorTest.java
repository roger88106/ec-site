package com.ecsite.common.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文字の長さを code point で数える {@link Size} の検証の単体テスト
 */
class CodePointSizeValidatorTest {

    /** サロゲートペアの文字（UTF-16 では 2、code point では 1） */
    private static final String PAIR = "𠮷";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private record Text(@Nullable @Size(max = 2) String value) {
    }

    private record MinText(@Nullable @Size(min = 2) String value) {
    }

    private record Items(@Nullable List<@Size(max = 2) String> values) {
    }

    @Test
    @DisplayName("サロゲートペアの文字 2 つは、上限 2 以内で有効")
    void acceptsPairsWithinMax() {
        // 操作と確認：UTF-16 では 4 でも、code point では 2 なので違反なし
        assertThat(validator.validate(new Text(PAIR.repeat(2)))).isEmpty();
    }

    @Test
    @DisplayName("サロゲートペアの文字 3 つは、上限 2 を超えて無効")
    void rejectsPairsOverMax() {
        // 操作と確認：違反が 1 つ
        assertThat(validator.validate(new Text(PAIR.repeat(3)))).hasSize(1);
    }

    @Test
    @DisplayName("null は有効")
    void acceptsNull() {
        // 操作と確認：違反なし
        assertThat(validator.validate(new Text(null))).isEmpty();
    }

    @Test
    @DisplayName("サロゲートペアの文字 1 つは、下限 2 に足りず無効")
    void rejectsPairsUnderMin() {
        // 操作と確認：UTF-16 では 2 でも、code point では 1 なので違反
        assertThat(validator.validate(new MinText(PAIR))).hasSize(1);
    }

    @Test
    @DisplayName("サロゲートペアの文字 2 つは、下限 2 ちょうどで有効")
    void acceptsPairsAtMin() {
        // 操作と確認：違反なし
        assertThat(validator.validate(new MinText(PAIR.repeat(2)))).isEmpty();
    }

    @Test
    @DisplayName("一覧の要素も、サロゲートペアの文字 2 つは有効")
    void acceptsPairsInListElements() {
        // 操作と確認：違反なし
        assertThat(validator.validate(new Items(List.of(PAIR.repeat(2), "a")))).isEmpty();
    }

    @Test
    @DisplayName("一覧の要素も、サロゲートペアの文字 3 つは無効")
    void rejectsPairsInListElements() {
        // 操作と確認：違反が 1 つ
        assertThat(validator.validate(new Items(List.of("a", PAIR.repeat(3))))).hasSize(1);
    }
}
