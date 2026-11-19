package com.ecsite.common.model.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文字の項目の判定の単体テスト
 */
class BoundedTextTest {

    // ============================== 定数 ==============================

    /**
     * サロゲートペアの文字（𠮷）
     *
     * <p>char では 2、code point では 1 と数える。
     */
    private static final String SURROGATE = "𠮷";

    /**
     * 文字数の上限
     */
    private static final int LIMIT = 3;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("長さ")
    class Length {

        @Test
        @DisplayName("サロゲートペアのない文字は、1 文字を 1 と数える")
        void countsCharacters() {
            // 操作と確認：3 文字
            assertThat(BoundedText.length("abc")).isEqualTo(3);
        }

        @Test
        @DisplayName("code point で数え、サロゲートペアは 1 文字と数える")
        void countsCodePoints() {
            // 操作と確認：サロゲートペアと 1 文字で 2 文字
            assertThat(BoundedText.length(SURROGATE + "a")).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("上限での切り詰め")
    class Truncate {

        @Test
        @DisplayName("上限ちょうどの文字列は、変わらない")
        void keepsTheLimit() {
            // 操作と確認：3 文字はそのまま
            assertThat(BoundedText.truncate("abc", LIMIT)).isEqualTo("abc");
        }

        @Test
        @DisplayName("上限を 1 文字超える文字列は、上限の文字数になる")
        void cutsOneOverTheLimit() {
            // 操作と確認：4 文字は先頭の 3 文字になる
            assertThat(BoundedText.truncate("abcd", LIMIT)).isEqualTo("abc");
        }

        @Test
        @DisplayName("上限より短い文字列は、変わらない")
        void keepsShortText() {
            // 操作と確認：空も含めて変わらない
            assertThat(BoundedText.truncate("", LIMIT)).isEmpty();
        }

        @Test
        @DisplayName("サロゲートペアは 1 文字と数え、上限ちょうどなら変わらない")
        void keepsSurrogatePairsAtTheLimit() {
            // 操作と確認：サロゲートペア 3 つはそのまま
            assertThat(BoundedText.truncate(SURROGATE.repeat(3), LIMIT)).isEqualTo(SURROGATE.repeat(3));
        }

        @Test
        @DisplayName("サロゲートペアの文字の途中で切らない")
        void doesNotSplitSurrogatePairs() {
            // 操作と確認：サロゲートペア 4 つは、3 つ（char で 6）になる。char の 3 つ目で切ると壊れる
            assertThat(BoundedText.truncate(SURROGATE.repeat(4), LIMIT)).isEqualTo(SURROGATE.repeat(3));
        }
    }

    @Nested
    @DisplayName("空白でなく上限以内（必須の項目）")
    class IsFilledWithin {

        @Test
        @DisplayName("上限ちょうどは通る")
        void acceptsTheLimit() {
            // 操作と確認：3 文字は通る
            assertThat(BoundedText.isFilledWithin("abc", LIMIT)).isTrue();
        }

        @Test
        @DisplayName("上限を 1 文字超えると通らない")
        void rejectsOverTheLimit() {
            // 操作と確認：4 文字は通らない
            assertThat(BoundedText.isFilledWithin("abcd", LIMIT)).isFalse();
        }

        @Test
        @DisplayName("サロゲートペアは 1 文字と数え、上限ちょうどは通る")
        void acceptsSurrogatePairsUpToTheLimit() {
            // 操作と確認：サロゲートペア 3 つは通る
            assertThat(BoundedText.isFilledWithin(SURROGATE.repeat(3), LIMIT)).isTrue();
        }

        @Test
        @DisplayName("サロゲートペアは 1 文字と数え、上限を 1 文字超えると通らない")
        void rejectsSurrogatePairsOverTheLimit() {
            // 操作と確認：サロゲートペア 4 つは通らない
            assertThat(BoundedText.isFilledWithin(SURROGATE.repeat(4), LIMIT)).isFalse();
        }

        @ParameterizedTest(name = "「{0}」")
        @NullAndEmptySource
        @ValueSource(strings = " 　")
        @DisplayName("null、空、空白だけは通らない")
        void rejectsBlank(String text) {
            // 操作と確認：通らない
            assertThat(BoundedText.isFilledWithin(text, LIMIT)).isFalse();
        }
    }

    @Nested
    @DisplayName("上限以内（空白を許す項目）")
    class FitsWithin {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"", "  ", "abc"})
        @DisplayName("上限以内なら、空や空白だけでも通る")
        void acceptsUpToTheLimit(String text) {
            // 操作と確認：通る
            assertThat(BoundedText.fitsWithin(text, LIMIT)).isTrue();
        }

        @Test
        @DisplayName("上限を超えると通らない")
        void rejectsOverTheLimit() {
            // 操作と確認：4 文字は通らない
            assertThat(BoundedText.fitsWithin("abcd", LIMIT)).isFalse();
        }

        @Test
        @DisplayName("null は通らない")
        void rejectsNull() {
            // 操作と確認：通らない
            assertThat(BoundedText.fitsWithin(null, LIMIT)).isFalse();
        }
    }
}
