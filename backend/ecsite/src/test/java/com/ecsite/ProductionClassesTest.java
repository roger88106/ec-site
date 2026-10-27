package com.ecsite;

import com.ecsite.contract.Contracts;
import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ProductionClasses}（構成の検査が読む本番のクラスの補助）の検証。
 */
class ProductionClassesTest {

    /**
     * 設計書のディレクトリ（Gradle がシステムプロパティで渡す）
     */
    private static final Path MODULE_DESIGNS = Contracts.path("design.modules.dir");

    /**
     * 設計書の表の「レベル」の行。値の最初の文字がレベルを表す
     */
    private static final Pattern LEVEL_ROW = Pattern.compile("^\\| レベル \\| (\\S)", Pattern.MULTILINE);

    /**
     * 補助の外に書かない、本番のクラスを読み込む呼び出し
     */
    private static final List<String> LOADING_CALLS = List.of(
            "importPackages" + "(", "importPackagesOf" + "(", "importClasspath" + "(", "importPath" + "(", "importLocations" + "(",
            "ApplicationModules" + ".of(", "@Analyze" + "Classes");

    @Test
    @DisplayName("手書きのクラスの一覧は、生成したコードを含まない")
    void handwrittenExcludesGeneratedCode() {
        var packages = packageNames(ProductionClasses.handwritten());

        assertThat(packages).isNotEmpty();
        assertThat(packages).noneMatch(name -> name.startsWith(ProductionClasses.API_PACKAGE + ".") || name.startsWith(ProductionClasses.JOOQ_PACKAGE));
        assertThat(packageNames(ProductionClasses.all()))
                .anyMatch(name -> name.startsWith(ProductionClasses.API_PACKAGE + "."))
                .anyMatch(name -> name.startsWith(ProductionClasses.JOOQ_PACKAGE));
    }

    @Test
    @DisplayName("モジュールのクラスの一覧は、モジュールの外のクラスを含まない")
    void modulesExcludeClassesOutsideModules() {
        var packages = packageNames(ProductionClasses.modules());

        assertThat(packages).isNotEmpty();
        assertThat(packages).allMatch(name -> name.startsWith(ProductionClasses.MODULE_PACKAGE_PREFIX));
        assertThat(packageNames(ProductionClasses.all())).anyMatch(name -> name.startsWith("com.ecsite.config"));
    }

    @Test
    @DisplayName("本番のクラスの一覧は、テストのクラスを含まない")
    void allExcludesTestClasses() {
        assertThat(ProductionClasses.all().stream().map(JavaClass::getName))
                .doesNotContain(ProductionClassesTest.class.getName());
    }

    @Test
    @DisplayName("本番のクラスの一覧とモジュールの一覧は、testFixtures のクラスを含まない（testFixtures は単体テストの classpath にある）")
    void productionClassesExcludeTestFixtures() throws ClassNotFoundException {
        Class<?> errorCodeCheck = Class.forName("com.ecsite.config.security.ErrorCodeCheck");
        Class<?> paymentGateway = Class.forName("com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway");

        assertThat(ProductionClasses.all().stream().map(JavaClass::getName))
                .isNotEmpty()
                .doesNotContain(errorCodeCheck.getName(), paymentGateway.getName());
        assertThat(ProductionClasses.applicationModules().stream())
                .noneMatch(module -> module.contains(paymentGateway));
    }

    @Test
    @DisplayName("本番のクラスの一覧は、tools のクラスを含まない（tools は単体テストの classpath にある）")
    void productionClassesExcludeTools() throws ClassNotFoundException {
        Class<?> tools = Class.forName("com.ecsite.tools.region.PostalCodeCsv");

        assertThat(ProductionClasses.all().stream().map(JavaClass::getName))
                .isNotEmpty()
                .noneMatch(name -> name.startsWith("com.ecsite.tools."))
                .doesNotContain(tools.getName());
    }

    @Test
    @DisplayName("パッケージの名前から、モジュールの名前を取り出す")
    void extractsModuleNameFromPackageName() {
        assertThat(ProductionClasses.moduleName("com.ecsite.module.order.domain.order.vo")).contains("order");
        assertThat(ProductionClasses.moduleName("com.ecsite.module.tax.api")).contains("tax");
        assertThat(ProductionClasses.moduleName("com.ecsite.module.tax")).contains("tax");
        assertThat(ProductionClasses.moduleName("com.ecsite.config.security")).isEmpty();
    }

    @Test
    @DisplayName("生成したコードのパッケージだけを、生成したコードと判定する")
    void detectsGeneratedPackages() {
        assertThat(ProductionClasses.isGenerated("com.ecsite.api.store")).isTrue();
        assertThat(ProductionClasses.isGenerated("com.ecsite.jooq")).isTrue();
        assertThat(ProductionClasses.isGenerated("com.ecsite.jooq.tables")).isTrue();
        assertThat(ProductionClasses.isGenerated("com.ecsite.module.catalog.api")).isFalse();
        assertThat(ProductionClasses.isGenerated("com.ecsite.config")).isFalse();
        assertThat(ProductionClasses.isGenerated("com.ecsite.jooqx")).isFalse();
        assertThat(ProductionClasses.isGenerated("com.ecsite.apix")).isFalse();
    }

    @Test
    @DisplayName("構成の検査は、本番のクラスを補助の外で読み込まない")
    void testsDoNotLoadProductionClassesOutsideHelper() throws IOException {
        var helper = Path.of("src/test/java/com/ecsite/ProductionClasses.java").toAbsolutePath().normalize();
        var violations = new TreeSet<String>();
        for (String root : List.of("src/test/java", "src/integrationTest/java", "src/testFixtures/java")) {
            try (Stream<Path> files = Files.walk(Path.of(root))) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    if (file.toAbsolutePath().normalize().equals(helper)) {
                        continue;
                    }
                    var source = Files.readString(file, StandardCharsets.UTF_8);
                    for (String call : LOADING_CALLS) {
                        if (source.contains(call)) {
                            violations.add(file + " : " + call);
                        }
                    }
                }
            }
        }

        assertThat(violations)
                .as("本番のクラスの読み込みは、補助 ProductionClasses から行う")
                .isEmpty();
    }

    @Test
    @DisplayName("層を指定すると、その層に本番のクラスがあるモジュールだけを返す（層を持たないモジュールを返さない）")
    void modulesWithReturnsOnlyModulesHavingTheLayer() {
        assertThat(ProductionClasses.modulesWith("domain")).contains("order", "catalog");
        assertThat(ProductionClasses.modulesWith("presentation")).contains("order").doesNotContain("payment");
        assertThat(ProductionClasses.modulesWith("presentation", "infrastructure")).contains("payment");
        assertThat(ProductionClasses.modulesWith("no-such-layer")).isEmpty();
    }

    @Test
    @DisplayName("対象が 0 件なら、説明を付けて失敗する")
    void requireNotEmptyFailsWithDescriptionWhenEmpty() {
        assertThatThrownBy(() -> ProductionClasses.requireNotEmpty(List.of(), "検査の対象のクラス"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("検査の対象のクラス");
    }

    @Test
    @DisplayName("対象が 1 件以上なら、通る")
    void requireNotEmptyPassesWhenNotEmpty() {
        ProductionClasses.requireNotEmpty(List.of("a"), "検査の対象のクラス");
    }

    @Test
    @DisplayName("レベル A のモジュールの一覧は、設計書の「レベル」の行が A のモジュールと一致する")
    void levelAModulesMatchDesignDocuments() throws IOException {
        Set<String> documented;
        try (Stream<Path> files = Files.list(MODULE_DESIGNS)) {
            documented = files
                    .filter(file -> file.getFileName().toString().endsWith(".md"))
                    .filter(ProductionClassesTest::isLevelA)
                    .map(file -> file.getFileName().toString().replaceFirst("\\.md$", ""))
                    .collect(Collectors.toCollection(TreeSet::new));
        }

        assertThat(documented).isNotEmpty();
        assertThat(ProductionClasses.LEVEL_A_MODULES).containsExactlyInAnyOrderElementsOf(documented);
    }

    /**
     * 設計書のレベルが A かを返す。
     *
     * @param file 設計書
     * @return 「レベル」の行の最初の文字が A なら true
     */
    private static boolean isLevelA(Path file) {
        try {
            Matcher matcher = LEVEL_ROW.matcher(Files.readString(file, StandardCharsets.UTF_8));
            return matcher.find() && matcher.group(1).equals("A");
        } catch (IOException e) {
            throw new IllegalStateException("設計書を読めない: " + file, e);
        }
    }

    /**
     * クラスのパッケージの名前の一覧を返す。
     *
     * @param classes クラス
     * @return パッケージの名前
     */
    private static Set<String> packageNames(Iterable<JavaClass> classes) {
        Set<String> names = new TreeSet<>();
        classes.forEach(javaClass -> names.add(javaClass.getPackageName()));
        return names;
    }
}
