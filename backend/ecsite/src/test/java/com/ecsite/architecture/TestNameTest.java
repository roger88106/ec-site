package com.ecsite.architecture;

import com.ecsite.architecture.sample.testcode.TestNameExamples;
import com.ecsite.support.ProductionClasses;
import com.ecsite.support.TestCodeClasses;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * テストの名前の検査
 *
 * <p>根拠はテストの書き方の「名前は振る舞いを書く」。
 * 単体テストと統合テストの、{@code @Test} と {@code @ParameterizedTest} の全てのメソッドに {@code @DisplayName} を付ける。
 */
class TestNameTest {

    // ============================== テスト ==============================

    @Test
    @DisplayName("単体テストと統合テストの全てのテストのメソッドに、表示の名前がある")
    void allTestMethodsHaveDisplayNames() {
        // 準備：対象のテストのメソッドが 1 件以上ある
        ProductionClasses.requireNotEmpty(testMethods(TestCodeClasses.all()), "テストのメソッド");

        // 操作と確認：表示の名前のないテストのメソッドがない
        assertThat(unnamed(TestCodeClasses.all())).isEmpty();
    }

    @Test
    @DisplayName("規則は、表示の名前のない @Test と @ParameterizedTest のメソッドだけに当たる")
    void ruleMatchesOnlyUnnamedTestMethods() {
        // 準備：名前のあるテストと、名前のないテストの例
        var examples = new ClassFileImporter().importClasses(TestNameExamples.class);

        // 操作と確認：名前のない 2 つだけが違反になる
        assertThat(unnamed(examples)).containsExactly(
                TestNameExamples.class.getName() + ".unnamed",
                TestNameExamples.class.getName() + ".unnamedParameterized");
    }

    // ============================== 補助 ==============================

    /**
     * 表示の名前のないテストのメソッドを返す。
     *
     * @param classes 調べるクラス
     * @return クラスの名前とメソッドの名前（名前の順）
     */
    private static List<String> unnamed(JavaClasses classes) {
        return testMethods(classes).stream()
                .filter(method -> !method.isAnnotatedWith(DisplayName.class))
                .map(method -> method.getOwner().getName() + "." + method.getName())
                .sorted()
                .toList();
    }

    /**
     * テストのメソッド（{@code @Test}、{@code @ParameterizedTest}）を返す。
     *
     * @param classes 調べるクラス
     * @return テストのメソッド
     */
    private static List<JavaMethod> testMethods(JavaClasses classes) {
        return classes.stream()
                .flatMap(javaClass -> javaClass.getMethods().stream())
                .filter(method -> method.isAnnotatedWith(Test.class) || method.isAnnotatedWith(ParameterizedTest.class))
                .toList();
    }
}
