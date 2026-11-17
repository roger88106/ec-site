package com.ecsite.architecture;

import com.ecsite.architecture.sample.staticonly.ExampleException;
import com.ecsite.architecture.sample.staticonly.ExampleKind;
import com.ecsite.architecture.sample.staticonly.ExampleRecord;
import com.ecsite.architecture.sample.staticonly.StaticOnlyNotFinal;
import com.ecsite.architecture.sample.staticonly.StaticOnlyWithPrivateConstructor;
import com.ecsite.architecture.sample.staticonly.StaticOnlyWithPublicConstructor;
import com.ecsite.architecture.sample.staticonly.WithInstanceField;
import com.ecsite.architecture.sample.staticonly.WithInstanceMethod;
import com.ecsite.support.ProductionClasses;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * インスタンスを作らないクラスの検査
 *
 * <p>根拠はバックエンドの書き方の「インスタンスを作らないクラス」。
 * static でないメンバーを持たない最上位のクラスは、final にし、コンストラクタをすべて private にする。
 * コンパイルした後のクラスを読むので、Lombok で作ったコンストラクタも見える。
 */
class StaticOnlyClassTest {

    // ============================== テスト ==============================

    @Test
    @DisplayName("static のメンバーだけの最上位のクラスは、コンストラクタがすべて private")
    void staticOnlyClassesHavePrivateConstructors() {
        // 準備：対象のクラスが 1 件以上ある
        ProductionClasses.requireNotEmpty(staticOnlyClasses(ProductionClasses.handwritten()), "static のメンバーだけの最上位のクラス");

        // 操作と確認：本番のクラスに、private でないコンストラクタを持つものがない
        assertThat(violations(ProductionClasses.handwritten())).isEmpty();
    }

    @Test
    @DisplayName("static のメンバーだけの最上位のクラスは final")
    void staticOnlyClassesAreFinal() {
        // 準備：対象のクラスが 1 件以上ある
        ProductionClasses.requireNotEmpty(staticOnlyClasses(ProductionClasses.handwritten()), "static のメンバーだけの最上位のクラス");

        // 操作と確認：本番のクラスに、final でないものがない
        assertThat(nonFinal(ProductionClasses.handwritten())).isEmpty();
    }

    @Test
    @DisplayName("final の規則は、final でない static のメンバーだけのクラスだけに当たる")
    void finalRuleMatchesOnlyNonFinalStaticOnlyClasses() {
        // 準備：final でない例と、規則に合う例・対象でない例
        var examples = new ClassFileImporter().importClasses(
                StaticOnlyNotFinal.class,
                StaticOnlyWithPrivateConstructor.class,
                WithInstanceMethod.class,
                ExampleException.class);

        // 操作と確認：final でない例だけが違反になる
        assertThat(nonFinal(examples)).containsExactly(StaticOnlyNotFinal.class.getName());
    }

    @Test
    @DisplayName("コンストラクタの規則は、private でないコンストラクタを持つ static のメンバーだけのクラスだけに当たる")
    void ruleMatchesOnlyStaticOnlyClassesWithOpenConstructors() {
        // 準備：public のコンストラクタを持つ例と、規則に合う例・対象でない例（インスタンスのメンバー、継承、enum、record）
        var examples = new ClassFileImporter().importClasses(
                StaticOnlyWithPublicConstructor.class,
                StaticOnlyWithPrivateConstructor.class,
                WithInstanceMethod.class,
                WithInstanceField.class,
                ExampleException.class,
                ExampleKind.class,
                ExampleRecord.class);

        // 操作と確認：public のコンストラクタを持つ例だけが違反になる
        assertThat(violations(examples)).containsExactly(StaticOnlyWithPublicConstructor.class.getName());
    }

    // ============================== 補助 ==============================

    /**
     * 規則に合わないクラスの名前を返す。
     *
     * @param classes 調べるクラス
     * @return static のメンバーだけで、private でないコンストラクタを持つ最上位のクラスの名前（名前の順）
     */
    private static List<String> violations(JavaClasses classes) {
        return staticOnlyClasses(classes).stream()
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
        return staticOnlyClasses(classes).stream()
                .filter(javaClass -> !javaClass.getModifiers().contains(JavaModifier.FINAL))
                .map(JavaClass::getName)
                .sorted()
                .toList();
    }

    /**
     * 規則の対象の、static のメンバーだけのクラスを返す。
     *
     * @param classes 調べるクラス
     * @return static のメンバーだけで、Spring がインスタンスを作らない最上位のクラス
     */
    private static List<JavaClass> staticOnlyClasses(JavaClasses classes) {
        return classes.stream()
                .filter(StaticOnlyClassTest::isTopLevelClass)
                .filter(StaticOnlyClassTest::hasOnlyStaticMembers)
                .filter(javaClass -> !isManagedBySpring(javaClass))
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
