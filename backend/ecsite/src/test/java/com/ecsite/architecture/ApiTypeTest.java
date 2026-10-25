package com.ecsite.architecture;

import com.ecsite.ProductionClasses;
import com.ecsite.architecture.apitype.module.sample.api.ExampleApi;
import com.ecsite.architecture.apitype.module.sample.api.ExampleApiInterface;
import com.ecsite.architecture.apitype.module.sample.application.ExtendsExampleApi;
import com.ecsite.architecture.apitype.module.sample.application.PlainService;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * モジュールの公開の API（{@code api/}）の型の検査（docs/rules/backend-coding.md 第 1.1 節）。<br />
 * {@code api/} は interface を持たないクラスで、{@code api/} の型を {@code api/} の外が実装・継承しない。
 * record と列挙は interface でないので対象にならない。
 */
class ApiTypeTest {

    /**
     * モジュールの公開の API のパッケージ（api とその下）の形
     */
    private static final Pattern API_PACKAGE = Pattern.compile(".*\\.module\\.[^.]+\\.api(\\..*)?");

    /**
     * package-info.java から作られるクラスの名前。コンパイラが interface として作るので、interface の数から除く
     */
    private static final String PACKAGE_INFO = "package-info";

    @Test
    @DisplayName("api に interface がない（対象は 1 件以上ある）")
    void apiHasNoInterface() {
        ProductionClasses.requireNotEmpty(apiClasses(ProductionClasses.modules()), "モジュールの api のクラス");

        assertThat(interfacesInApi(ProductionClasses.modules())).isEmpty();
    }

    @Test
    @DisplayName("api の型を、api の外が実装・継承しない")
    void apiTypesAreNotExtendedOutsideApi() {
        assertThat(subtypesOutsideApi(ProductionClasses.handwritten())).isEmpty();
    }

    @Test
    @DisplayName("interface の規則は、api の interface の例に当たり、interface でないクラスには当たらない")
    void interfaceRuleMatchesInterfaceExample() {
        JavaClasses examples = new ClassFileImporter().importClasses(ExampleApiInterface.class, ExampleApi.class);

        assertThat(interfacesInApi(examples)).containsExactly(ExampleApiInterface.class.getName());
    }

    @Test
    @DisplayName("継承の規則は、api の外から api の型を継承する例に当たり、継承しないクラスには当たらない")
    void extensionRuleMatchesSubtypeOutsideApiExample() {
        JavaClasses examples = new ClassFileImporter().importClasses(
                ExampleApi.class, ExtendsExampleApi.class, PlainService.class);

        assertThat(subtypesOutsideApi(examples)).containsExactly(ExtendsExampleApi.class.getName());
    }

    /**
     * モジュールの api のクラスを返す。
     *
     * @param classes 調べるクラス
     * @return api（とその下）のパッケージのクラス
     */
    private static List<JavaClass> apiClasses(JavaClasses classes) {
        return classes.stream().filter(ApiTypeTest::isInApi).toList();
    }

    /**
     * api にある interface の名前を返す。
     *
     * @param classes 調べるクラス
     * @return api のパッケージにある interface の名前（名前の順）。注釈と package-info は interface に数えない
     */
    private static List<String> interfacesInApi(JavaClasses classes) {
        return apiClasses(classes).stream()
                .filter(JavaClass::isInterface)
                .filter(javaClass -> !javaClass.isAnnotation())
                .filter(javaClass -> !PACKAGE_INFO.equals(javaClass.getSimpleName()))
                .map(JavaClass::getName)
                .sorted()
                .toList();
    }

    /**
     * api の型を継承・実装している、api の外のクラスの名前を返す。
     *
     * @param classes 調べるクラス
     * @return api の外にあり、api の型を親のクラスか interface に持つクラスの名前（名前の順）
     */
    private static List<String> subtypesOutsideApi(JavaClasses classes) {
        return classes.stream()
                .filter(javaClass -> !isInApi(javaClass))
                .filter(javaClass -> javaClass.getRawSuperclass().stream().anyMatch(ApiTypeTest::isInApi)
                        || javaClass.getRawInterfaces().stream().anyMatch(ApiTypeTest::isInApi))
                .map(JavaClass::getName)
                .sorted()
                .toList();
    }

    /**
     * クラスが、モジュールの api のパッケージにあるかを返す。
     *
     * @param javaClass クラス
     * @return api（とその下）のパッケージなら true
     */
    private static boolean isInApi(JavaClass javaClass) {
        return API_PACKAGE.matcher(javaClass.getPackageName()).matches();
    }
}
