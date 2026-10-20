package com.ecsite.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * インスタンスを作らないクラスの検査（docs/backend/coding.md 第 5.3 節）。<br />
 * static でないメンバーを持たない最上位のクラスは、final にし、コンストラクタをすべて private にする。
 * コンパイルした後のクラスを読むので、Lombok で作ったコンストラクタも見える。
 */
class StaticOnlyClassTest {

    /**
     * 本番のクラス（テストと、生成したコードを除く）
     */
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(location -> !location.contains("/com/ecsite/jooq/") && !location.contains("/com/ecsite/api/"))
            .importPackages("com.ecsite");

    @Test
    @DisplayName("static のメンバーだけの最上位のクラスは、コンストラクタがすべて private")
    void staticOnlyClassesHavePrivateConstructors() {
        assertThat(violations(CLASSES)).isEmpty();
    }

    @Test
    @DisplayName("static のメンバーだけの最上位のクラスは final")
    void staticOnlyClassesAreFinal() {
        assertThat(nonFinal(CLASSES)).isEmpty();
    }

    @Test
    @DisplayName("final の規則は、final でない static のメンバーだけのクラスに当たり、ほかには当たらない")
    void finalRuleMatchesOnlyNonFinalStaticOnlyClasses() {
        var examples = new ClassFileImporter().importClasses(
                StaticOnlyNotFinal.class,
                StaticOnlyWithPrivateConstructor.class,
                WithInstanceMethod.class,
                ExampleException.class);

        assertThat(nonFinal(examples)).containsExactly(StaticOnlyNotFinal.class.getName());
    }

    @Test
    @DisplayName("規則は、private でないコンストラクタを持つ static のメンバーだけのクラスに当たり、ほかには当たらない")
    void ruleMatchesOnlyStaticOnlyClassesWithOpenConstructors() {
        var examples = new ClassFileImporter().importClasses(
                StaticOnlyWithPublicConstructor.class,
                StaticOnlyWithPrivateConstructor.class,
                WithInstanceMethod.class,
                WithInstanceField.class,
                ExampleException.class,
                ExampleKind.class,
                ExampleRecord.class);

        assertThat(violations(examples)).containsExactly(StaticOnlyWithPublicConstructor.class.getName());
    }

    /**
     * 規則に合わないクラスの名前を返す。
     *
     * @param classes 調べるクラス
     * @return static のメンバーだけで、private でないコンストラクタを持つ最上位のクラスの名前（名前の順）
     */
    private static List<String> violations(JavaClasses classes) {
        return classes.stream()
                .filter(StaticOnlyClassTest::isTopLevelClass)
                .filter(StaticOnlyClassTest::hasOnlyStaticMembers)
                .filter(javaClass -> !isManagedBySpring(javaClass))
                .filter(javaClass -> javaClass.getConstructors().stream()
                        .anyMatch(constructor -> !constructor.getModifiers().contains(JavaModifier.PRIVATE)))
                .map(JavaClass::getName)
                .sorted()
                .toList();
    }

    /**
     * final でない、static のメンバーだけのクラスの名前を返す。
     *
     * @param classes 調べるクラス
     * @return static のメンバーだけで final でない最上位のクラスの名前（名前の順）
     */
    private static List<String> nonFinal(JavaClasses classes) {
        return classes.stream()
                .filter(StaticOnlyClassTest::isTopLevelClass)
                .filter(StaticOnlyClassTest::hasOnlyStaticMembers)
                .filter(javaClass -> !isManagedBySpring(javaClass))
                .filter(javaClass -> !javaClass.getModifiers().contains(JavaModifier.FINAL))
                .map(JavaClass::getName)
                .sorted()
                .toList();
    }

    /**
     * 最上位の普通のクラスかを返す。
     *
     * @param javaClass クラス
     * @return enum・record・interface・注釈・入れ子のクラス・合成のクラスでなく、Object を直に継ぐなら true
     *         （ほかのクラスを継ぐクラスは、親のインスタンスのメンバーを持つ）
     */
    private static boolean isTopLevelClass(JavaClass javaClass) {
        return javaClass.isTopLevelClass()
                && !javaClass.isEnum()
                && !javaClass.isRecord()
                && !javaClass.isInterface()
                && !javaClass.isAnnotation()
                && !javaClass.getModifiers().contains(JavaModifier.SYNTHETIC)
                && javaClass.getRawSuperclass().map(superclass -> superclass.isEquivalentTo(Object.class)).orElse(true);
    }

    /**
     * static でないメンバー（項目、メソッド）を持たないかを返す。
     *
     * @param javaClass クラス
     * @return 項目とメソッドがすべて static なら true（コンストラクタは数えない）
     */
    private static boolean hasOnlyStaticMembers(JavaClass javaClass) {
        return javaClass.getFields().stream().allMatch(field -> field.getModifiers().contains(JavaModifier.STATIC))
                && javaClass.getMethods().stream()
                        .filter(method -> !method.getModifiers().contains(JavaModifier.SYNTHETIC))
                        .allMatch(method -> method.getModifiers().contains(JavaModifier.STATIC));
    }

    /**
     * Spring がインスタンスを作るクラス（起動のクラス、設定のクラスなど）かを返す。
     *
     * @param javaClass クラス
     * @return Spring の注釈が付いていれば true。static の Bean の定義だけでも、Spring がインスタンスを作る
     */
    private static boolean isManagedBySpring(JavaClass javaClass) {
        return javaClass.getAnnotations().stream()
                .anyMatch(annotation -> annotation.getRawType().getPackageName().startsWith("org.springframework"));
    }
}

/**
 * 規則に当たる例：static のメンバーだけで、コンストラクタが public
 */
final class StaticOnlyWithPublicConstructor {

    /**
     * 例の定数
     */
    static final int LIMIT = 1;

    /**
     * 例のコンストラクタ（規則に合わない）
     */
    StaticOnlyWithPublicConstructor() {
    }

    /**
     * 例の関数。
     *
     * @return 定数
     */
    static int limit() {
        return LIMIT;
    }
}

/**
 * 規則に合う例：static のメンバーだけで、コンストラクタが private
 */
final class StaticOnlyWithPrivateConstructor {

    /**
     * 作らせない
     */
    private StaticOnlyWithPrivateConstructor() {
    }

    /**
     * 例の関数。
     *
     * @return 1
     */
    static int one() {
        return 1;
    }
}

/**
 * final の規則に当たる例：static のメンバーだけで、コンストラクタは private だが final でない
 */
class StaticOnlyNotFinal {

    /**
     * 作らせない
     */
    private StaticOnlyNotFinal() {
    }

    /**
     * 例の関数。
     *
     * @return 2
     */
    static int two() {
        return 2;
    }
}

/**
 * 当たらない例：static でないメソッドを持つ
 */
final class WithInstanceMethod {

    /**
     * 例のメソッド。
     *
     * @return 1
     */
    int one() {
        return 1;
    }
}

/**
 * 当たらない例：static でない項目を持つ
 */
final class WithInstanceField {

    /**
     * 例の項目
     */
    int value;
}

/**
 * 当たらない例：ほかのクラスを継ぐ（自分のメンバーはコンストラクタだけ）
 */
class ExampleException extends RuntimeException {

    /**
     * @param message 例の文
     */
    ExampleException(String message) {
        super(message);
    }
}

/**
 * 当たらない例：enum
 */
enum ExampleKind {
    /**
     * 例の値
     */
    ONE
}

/**
 * 当たらない例：record
 *
 * @param value 例の項目
 */
record ExampleRecord(int value) {
}
