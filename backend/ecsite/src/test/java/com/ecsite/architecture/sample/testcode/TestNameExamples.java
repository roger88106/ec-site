package com.ecsite.architecture.sample.testcode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * テストの名前の検査の例：表示の名前のあるテストのメソッドと、ないテストのメソッド
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 * JUnit が実行しないよう、抽象クラスにする。
 */
public abstract class TestNameExamples {

    /**
     * 表示の名前のあるテスト（規則に合う）。
     */
    @Test
    @DisplayName("名前のある例")
    void named() {
    }

    /**
     * 表示の名前のないテスト（規則に合わない）。
     */
    @Test
    void unnamed() {
    }

    /**
     * 表示の名前のない、引数を変えて繰り返すテスト（規則に合わない）。
     *
     * @param value 値
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void unnamedParameterized(int value) {
    }
}
