package com.ecsite.common.conversion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 列挙の名前による変換の単体テスト
 */
class EnumConversionTest {

    // ============================== テスト用の列挙 ==============================

    private enum SourceColor {
        RED, GREEN, BLUE
    }

    private enum SameNameColor {
        RED, GREEN, BLUE
    }

    private enum PartialColor {
        RED, GREEN
    }

    // ============================== テスト ==============================

    @ParameterizedTest
    @EnumSource(SourceColor.class)
    @DisplayName("変換元の定数と同じ名前の、変換先の定数を返す")
    void returnsConstantWithSameName(SourceColor source) {
        // 操作：変換元の全ての定数を変換する
        var target = EnumConversion.byName(source, SameNameColor.class);

        // 確認：同じ名前の定数が返る
        assertThat(target.name()).isEqualTo(source.name());
    }

    @Test
    @DisplayName("変換先に同名の定数がないとき、変換元と変換先の型の名前と定数の名前を入れた例外を投げる")
    void throwsWhenTargetHasNoConstantWithSameName() {
        // 準備：変換先（RED と GREEN だけ）にない定数
        var source = SourceColor.BLUE;

        // 操作と確認：IllegalArgumentException で、メッセージに型の名前と定数の名前が入る
        assertThatThrownBy(() -> EnumConversion.byName(source, PartialColor.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SourceColor.class.getSimpleName())
                .hasMessageContaining(PartialColor.class.getSimpleName())
                .hasMessageContaining("BLUE");
    }

    @Test
    @DisplayName("変換先に同名の定数がある定数は、変換先の定数が少なくても変換できる")
    void convertsConstantsPresentInSmallerTarget() {
        // 操作と確認：変換先にある定数は、同じ名前の定数が返る
        assertThat(EnumConversion.byName(SourceColor.RED, PartialColor.class)).isEqualTo(PartialColor.RED);
        assertThat(EnumConversion.byName(SourceColor.GREEN, PartialColor.class)).isEqualTo(PartialColor.GREEN);
    }
}
