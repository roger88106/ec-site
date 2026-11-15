package com.ecsite;

import com.ecsite.contract.Contracts;
import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本番のクラスの補助 {@link ProductionClasses} の検証
 *
 * <p>構成の検査が読む本番のクラスの補助を対象にする。
 */
class ProductionClassesTest {

    // ============================== 定数 ==============================

    /**
     * モジュールの設計書のディレクトリ（Gradle が system property で渡すリポジトリのルートから）
     */
    private static final Path MODULE_DESIGNS = Contracts.path("docs/02-設計");

    /**
     * バックエンドのモジュールのディレクトリ
     *
     * <p>テストの作業ディレクトリ {@code backend/ecsite} から見た場所。名前がモジュールの設計書のファイル名になる。
     */
    private static final String MODULE_SOURCES = "src/main/java/com/ecsite/module";

    /**
     * モジュールの設計書の表の「レベル」の行
     *
     * <p>値の最初の文字がレベルを表し、A、B、C のどれかでなければ一致しない（{@code **A**} などは一致しない）。
     */
    private static final Pattern LEVEL_ROW = Pattern.compile("^\\| レベル \\| ([ABC])", Pattern.MULTILINE);

    /**
     * レベルの行を読めなかったときの値（失敗の表示に使う）
     */
    private static final String NO_LEVEL_ROW = "② にレベルの行がない、または形が違う";

    /**
     * 補助の外に書かない、本番のクラスを読み込む呼び出し
     */
    private static final List<String> LOADING_CALLS = List.of(
            "importPackages" + "(", "importPackagesOf" + "(", "importClasspath" + "(", "importPath" + "(", "importLocations" + "(",
            "ApplicationModules" + ".of(", "@Analyze" + "Classes");

    /**
     * 検査の対象の説明（失敗のメッセージに含める）
     */
    private static final String DESCRIPTION = "検査の対象のクラス";

    // ============================== テスト ==============================

    @Nested
    @DisplayName("本番のクラスの一覧")
    class ClassLists {

        @Test
        @DisplayName("手書きのクラスの一覧は、生成したコードを含まない")
        void handwrittenExcludesGeneratedCode() {
            // 確認（前提）：全ての本番のクラスには、生成したコード（OpenAPI と jOOQ）がある
            assertThat(packageNames(ProductionClasses.all()))
                    .anyMatch(name -> name.startsWith(ProductionClasses.API_PACKAGE + "."))
                    .anyMatch(name -> name.startsWith(ProductionClasses.JOOQ_PACKAGE));

            // 操作：手書きのクラスのパッケージを読む
            var packages = packageNames(ProductionClasses.handwritten());

            // 確認：1 つ以上あり、生成したコードのパッケージがない
            assertThat(packages).isNotEmpty();
            assertThat(packages).noneMatch(name -> name.startsWith(ProductionClasses.API_PACKAGE + ".") || name.startsWith(ProductionClasses.JOOQ_PACKAGE));
        }

        @Test
        @DisplayName("モジュールのクラスの一覧は、モジュールの外のクラスを含まない")
        void modulesExcludeClassesOutsideModules() {
            // 確認（前提）：全ての本番のクラスには、モジュールの外の設定のクラスがある
            assertThat(packageNames(ProductionClasses.all())).anyMatch(name -> name.startsWith("com.ecsite.config"));

            // 操作：モジュールのクラスのパッケージを読む
            var packages = packageNames(ProductionClasses.modules());

            // 確認：1 つ以上あり、全てモジュールのパッケージ
            assertThat(packages).isNotEmpty();
            assertThat(packages).allMatch(name -> name.startsWith(ProductionClasses.MODULE_PACKAGE_PREFIX));
        }

        @Test
        @DisplayName("本番のクラスの一覧は、テストのクラスを含まない")
        void allExcludesTestClasses() {
            // 操作と確認：このテストのクラスがない
            assertThat(ProductionClasses.all().stream().map(JavaClass::getName))
                    .doesNotContain(ProductionClassesTest.class.getName());
        }

        @Test
        @DisplayName("本番のクラスの一覧は、testFixtures のクラスを含まない（testFixtures は単体テストの classpath にある）")
        void allExcludesTestFixtures() throws ClassNotFoundException {
            // 準備：testFixtures のクラスが単体テストの classpath にある
            Class<?> errorCodeCheck = Class.forName("com.ecsite.config.security.ErrorCodeCheck");
            Class<?> paymentGateway = Class.forName("com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway");

            // 操作と確認：本番のクラスの一覧に、どちらもない
            assertThat(ProductionClasses.all().stream().map(JavaClass::getName))
                    .isNotEmpty()
                    .doesNotContain(errorCodeCheck.getName(), paymentGateway.getName());
        }

        @Test
        @DisplayName("モジュールの一覧は、testFixtures のクラスを含まない")
        void applicationModulesExcludeTestFixtures() throws ClassNotFoundException {
            // 準備：testFixtures の決済サービスの mock が単体テストの classpath にある
            Class<?> paymentGateway = Class.forName("com.ecsite.module.payment.infrastructure.mock.TestPaymentGateway");

            // 操作と確認：どのモジュールにも含まれない
            assertThat(ProductionClasses.applicationModules().stream())
                    .noneMatch(module -> module.contains(paymentGateway));
        }

        @Test
        @DisplayName("本番のクラスの一覧は、tools のクラスを含まない（tools は単体テストの classpath にある）")
        void allExcludesTools() throws ClassNotFoundException {
            // 準備：tools のクラスが単体テストの classpath にある
            Class<?> tools = Class.forName("com.ecsite.tools.region.PostalCodeCsv");

            // 操作と確認：tools のパッケージのクラスがない
            assertThat(ProductionClasses.all().stream().map(JavaClass::getName))
                    .isNotEmpty()
                    .noneMatch(name -> name.startsWith("com.ecsite.tools."))
                    .doesNotContain(tools.getName());
        }
    }

    @Nested
    @DisplayName("パッケージの名前の判定")
    class PackageNames {

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({
                "com.ecsite.module.order.domain.order.vo, order",
                "com.ecsite.module.tax.api, tax",
                "com.ecsite.module.tax, tax"})
        @DisplayName("モジュールのパッケージ（直下を含む）の名前から、モジュールの名前を取り出す")
        void extractsModuleNameFromPackageName(String packageName, String moduleName) {
            // 操作と確認：モジュールの名前が取り出せる
            assertThat(ProductionClasses.moduleName(packageName)).contains(moduleName);
        }

        @Test
        @DisplayName("モジュールの外のパッケージの名前からは、モジュールの名前を取り出さない")
        void extractsNothingOutsideModules() {
            // 操作と確認：空
            assertThat(ProductionClasses.moduleName("com.ecsite.config.security")).isEmpty();
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"com.ecsite.api.store", "com.ecsite.jooq", "com.ecsite.jooq.tables"})
        @DisplayName("生成したコードのパッケージを、生成したコードと判定する")
        void detectsGeneratedPackages(String packageName) {
            // 操作と確認：生成したコード
            assertThat(ProductionClasses.isGenerated(packageName)).isTrue();
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"com.ecsite.module.catalog.api", "com.ecsite.config", "com.ecsite.jooqx", "com.ecsite.apix"})
        @DisplayName("生成したコードの外のパッケージは、名前の先頭が同じでも、生成したコードと判定しない")
        void doesNotDetectHandwrittenPackages(String packageName) {
            // 操作と確認：生成したコードでない
            assertThat(ProductionClasses.isGenerated(packageName)).isFalse();
        }
    }

    @Nested
    @DisplayName("層を持つモジュール")
    class ModulesWithLayer {

        @Test
        @DisplayName("層を指定すると、その層に本番のクラスがあるモジュールを返す")
        void returnsModulesHavingTheLayer() {
            // 操作と確認：domain を持つ注文と商品を返す
            assertThat(ProductionClasses.modulesWith("domain")).contains("order", "catalog");
        }

        @Test
        @DisplayName("指定した層を持たないモジュールは返さない")
        void excludesModulesWithoutTheLayer() {
            // 操作と確認：presentation を持たない支払を返さない
            assertThat(ProductionClasses.modulesWith("presentation")).contains("order").doesNotContain("payment");
        }

        @Test
        @DisplayName("複数の層を指定すると、どれかの層に本番のクラスがあるモジュールを返す")
        void returnsModulesHavingAnyOfTheLayers() {
            // 操作と確認：presentation を持たないが infrastructure を持つ支払を返す
            assertThat(ProductionClasses.modulesWith("presentation", "infrastructure")).contains("payment");
        }

        @Test
        @DisplayName("どのモジュールも持たない層を指定すると、空")
        void returnsNothingForUnknownLayer() {
            // 操作と確認：空
            assertThat(ProductionClasses.modulesWith("no-such-layer")).isEmpty();
        }
    }

    @Nested
    @DisplayName("検査の対象が 0 件でないことの確認")
    class RequireNotEmpty {

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("com.ecsite.ProductionClassesTest#emptyTargets")
        @DisplayName("対象が 0 件なら、集まりの種類によらず、規則が常に通る状態であることと対象の説明を書いて失敗する")
        void failsWithDescriptionWhenEmpty(String kind, Collection<?> targets) {
            // 操作と確認：対象の説明を含む失敗
            assertThatThrownBy(() -> ProductionClasses.requireNotEmpty(targets, DESCRIPTION))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("検査の対象が 0 件で、規則が常に通る状態になっている: " + DESCRIPTION);
        }

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("com.ecsite.ProductionClassesTest#nonEmptyTargets")
        @DisplayName("対象が 1 件以上なら、集まりの種類によらず失敗しない")
        void passesWhenNotEmpty(String kind, Collection<?> targets) {
            // 確認（前提）：対象が 1 件以上ある
            assertThat(targets).isNotEmpty();

            // 操作と確認：失敗しない
            assertThatCode(() -> ProductionClasses.requireNotEmpty(targets, DESCRIPTION)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("モジュールのレベル")
    class Levels {

        @Test
        @DisplayName("レベル A のモジュールの一覧は、モジュールの設計書の「レベル」の行が A のモジュールと一致する")
        void levelAModulesMatchDesignDocuments() throws IOException {
            // 準備：設計書のレベルが A のモジュールを集める
            var documented = new TreeSet<String>();
            for (String module : moduleDirectories()) {
                if (levelOf(module).equals("A")) {
                    documented.add(module);
                }
            }

            // 確認（前提）：1 つ以上ある
            assertThat(documented).isNotEmpty();

            // 操作と確認：補助のレベル A の一覧と同じ
            assertThat(ProductionClasses.LEVEL_A_MODULES).containsExactlyInAnyOrderElementsOf(documented);
        }

        @Test
        @DisplayName("バックエンドのモジュールごとに、モジュールの設計書にレベルの行がある（共通の設計書は読まない）")
        void everyModuleDesignHasLevelRow() throws IOException {
            // 準備：バックエンドのモジュール
            var modules = moduleDirectories();

            // 確認（前提）：1 つ以上ある
            assertThat(modules).isNotEmpty();

            // 操作と確認：どのモジュールの設計書にも、A、B、C のどれかのレベルの行がある
            for (String module : modules) {
                assertThat(levelOf(module)).as("docs/02-設計/%s.md のレベルの行", module).isIn("A", "B", "C");
            }
        }
    }

    @Test
    @DisplayName("構成の検査は、本番のクラスを補助の外で読み込まない")
    void testsDoNotLoadProductionClassesOutsideHelper() throws IOException {
        // 準備：補助のファイル
        var helper = Path.of("src/test/java/com/ecsite/ProductionClasses.java").toAbsolutePath().normalize();

        // 操作：補助の外のテストのファイルから、本番のクラスを読み込む呼び出しを探す
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

        // 確認：ない
        assertThat(violations)
                .as("本番のクラスの読み込みは、補助 ProductionClasses から行う")
                .isEmpty();
    }

    // ============================== 準備の補助 ==============================

    /**
     * 0 件の検査の対象を、集まりの種類ごとに返す。
     *
     * @return 集まりの種類の名前と、検査の対象
     */
    static Stream<Arguments> emptyTargets() {
        return Stream.of(
                Arguments.of("一覧", List.of()),
                Arguments.of("集合", Set.of()),
                Arguments.of("表の項目", Map.of().entrySet()));
    }

    /**
     * 1 件以上の検査の対象を、集まりの種類ごとに返す。
     *
     * <p>構成の検査が渡す集まり（一覧、集合、表の項目）の形をそろえる。
     *
     * @return 集まりの種類の名前と、検査の対象
     */
    static Stream<Arguments> nonEmptyTargets() {
        return Stream.of(
                Arguments.of("1 件の一覧", List.of("a")),
                Arguments.of("複数件の集合", Set.of("a", "b")),
                Arguments.of("表の項目", Map.of("a", 1).entrySet()));
    }

    /**
     * バックエンドのモジュールのディレクトリの名前を返す。
     *
     * @return {@code src/main/java/com/ecsite/module} の下のディレクトリの名前
     * @throws IOException ディレクトリを読めないとき
     */
    private static Set<String> moduleDirectories() throws IOException {
        try (Stream<Path> dirs = Files.list(Path.of(MODULE_SOURCES))) {
            return dirs.filter(Files::isDirectory)
                    .map(dir -> dir.getFileName().toString())
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    /**
     * モジュールの設計書の「レベル」の行の値（A、B、C）を返す。
     *
     * @param module モジュールの名前（設計書のファイル名と同じ）
     * @return レベル。設計書がない、行がない、または形が違うときは「② にレベルの行がない、または形が違う」の文字列
     * @throws IOException 設計書を読めないとき
     */
    private static String levelOf(String module) throws IOException {
        Path file = MODULE_DESIGNS.resolve(module + ".md");
        if (!Files.exists(file)) {
            return NO_LEVEL_ROW;
        }
        Matcher matcher = LEVEL_ROW.matcher(Files.readString(file, StandardCharsets.UTF_8));
        return matcher.find() ? matcher.group(1) : NO_LEVEL_ROW;
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
