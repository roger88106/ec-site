package com.ecsite.common.model.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文字の項目の判定の単体テスト
 */
class BoundedTextTest {

    /**
     * サロゲートペアの文字（𠮷）。char では 2、code point では 1 と数える
     */
    private static final String SURROGATE = "𠮷";

    @Nested
    @DisplayName("長さ")
    class Length {

        @Test
        @DisplayName("code point で数える（サロゲートペアは 1 文字）")
        void countsCodePoints() {
            assertThat(BoundedText.length("abc")).isEqualTo(3);
            assertThat(BoundedText.length(SURROGATE + "a")).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("空白でなく上限以内（必須の項目）")
    class IsFilledWithin {

        @Test
        @DisplayName("上限ちょうどは通り、1 文字超えると通らない")
        void acceptsUpToTheLimit() {
            assertThat(BoundedText.isFilledWithin("abc", 3)).isTrue();
            assertThat(BoundedText.isFilledWithin("abcd", 3)).isFalse();
        }

        @Test
        @DisplayName("サロゲートペアは 1 文字と数える")
        void countsSurrogatePairAsOne() {
            assertThat(BoundedText.isFilledWithin(SURROGATE.repeat(3), 3)).isTrue();
            assertThat(BoundedText.isFilledWithin(SURROGATE.repeat(4), 3)).isFalse();
        }

        @Test
        @DisplayName("null、空、空白だけは通らない")
        void rejectsBlank() {
            assertThat(BoundedText.isFilledWithin(null, 3)).isFalse();
            assertThat(BoundedText.isFilledWithin("", 3)).isFalse();
            assertThat(BoundedText.isFilledWithin(" 　", 3)).isFalse();
        }
    }

    @Nested
    @DisplayName("上限以内（空白を許す項目）")
    class FitsWithin {

        @Test
        @DisplayName("空と空白は通り、上限を超えると通らない")
        void acceptsBlankUpToTheLimit() {
            assertThat(BoundedText.fitsWithin("", 3)).isTrue();
            assertThat(BoundedText.fitsWithin("  ", 3)).isTrue();
            assertThat(BoundedText.fitsWithin("abc", 3)).isTrue();
            assertThat(BoundedText.fitsWithin("abcd", 3)).isFalse();
        }

        @Test
        @DisplayName("null は通らない")
        void rejectsNull() {
            assertThat(BoundedText.fitsWithin(null, 3)).isFalse();
        }
    }
}
