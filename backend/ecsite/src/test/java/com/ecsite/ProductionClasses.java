package com.ecsite;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.springframework.modulith.core.ApplicationModules;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 構成の検査が読む、本番のクラスの補助。<br />
 * クラスファイルの読み込みは重いので、JVM ごとに 1 回だけ行い、各検査はここから絞り込んだ結果を受け取る。
 * 検査ごとに読み込むと、同じクラスの複製がテストの JVM のメモリに重ねて残る。
 * 違反する例のクラスの読み込みは本番のクラスの読み込みではないので、この補助を通さず、検査の側で行う。
 */
public final class ProductionClasses {

    /**
     * モジュールの一覧から除くテストのクラスの条件（名前が Test で終わるクラスと、その入れ子のクラス、
     * 単体テストと統合テストが共有する testFixtures のクラス）。<br />
     * テストは準備のために他のモジュールの内部（リポジトリなど）を直接使うことがあるので、境界の検証から除く。
     * 検証するのは本番のコードの境界。
     */
    public static final DescribedPredicate<JavaClass> TEST_CLASSES = DescribedPredicate.describe(
            "test classes", javaClass -> javaClass.getName().matches(".*Test(\\$.*)?") || isTestFixture(javaClass));

    /**
     * レベル A のモジュール（バックエンドのアーキテクチャ 第 2 節）。<br />
     * docs/design/modules の各設計書の「レベル」と一致することは、{@code ProductionClassesTest} が確認する。
     */
    public static final List<String> LEVEL_A_MODULES = List.of("tax", "catalog", "inventory", "order", "payment");

    /**
     * OpenAPI から生成した interface と型のパッケージ
     */
    private static final String API_PACKAGE = "com.ecsite.api";

    /**
     * jOOQ が生成したコードのパッケージ
     */
    private static final String JOOQ_PACKAGE = "com.ecsite.jooq";

    /**
     * モジュールのパッケージの接頭辞
     */
    private static final String MODULE_PACKAGE_PREFIX = "com.ecsite.module.";

    /**
     * パッケージの名前から、モジュールの名前と、モジュールの直下のパッケージの名前を取り出す形
     */
    private static final Pattern MODULE_SUBPACKAGE = Pattern.compile("^com\\.ecsite\\.module\\.([^.]+)\\.([^.]+)");

    /**
     * パッケージの名前から、モジュールの名前を取り出す形（モジュールの直下のパッケージも含む）
     */
    private static final Pattern MODULE_NAME = Pattern.compile("^com\\.ecsite\\.module\\.([^.]+)");

    /**
     * インスタンスを作らない。
     */
    private ProductionClasses() {
    }

    /**
     * 本番のクラス（テストを除く）を返す。生成したコードを含む。
     *
     * @return {@code com.ecsite} の本番のクラス
     */
    public static JavaClasses all() {
        return Holder.ALL;
    }

    /**
     * 手書きの本番のクラスを返す。{@link #all()} から生成したコードを除いたもの。<br />
     * 生成したコードのクラスも依存先としては読み込まれているので、生成したコードへの依存は見える。
     *
     * @return 生成したコードを除いた本番のクラス
     */
    public static JavaClasses handwritten() {
        return Holder.HANDWRITTEN;
    }

    /**
     * モジュールの本番のクラスを返す。{@link #all()} から {@code com.ecsite.module} の外を除いたもの。
     *
     * @return {@code com.ecsite.module} の本番のクラス
     */
    public static JavaClasses modules() {
        return Holder.MODULES;
    }

    /**
     * Spring Modulith のモジュールの一覧（テストのクラスを除く）を返す。
     *
     * @return モジュールの一覧
     */
    public static ApplicationModules applicationModules() {
        return ModulesHolder.APPLICATION_MODULES;
    }

    /**
     * 指定した層のどれかに、本番のクラスがあるモジュールの名前を返す。<br />
     * 層を持たないモジュールで繰り返すと、規則の対象が 0 件になって常に通るので、層を持つモジュールだけを返す。
     *
     * @param layers 層の名前
     * @return モジュールの名前（名前の順）
     */
    public static Set<String> modulesWith(String... layers) {
        Set<String> wanted = Set.of(layers);
        return modules().stream()
                .map(JavaClass::getPackageName)
                .map(MODULE_SUBPACKAGE::matcher)
                .filter(Matcher::find)
                .filter(matcher -> wanted.contains(matcher.group(2)))
                .map(matcher -> matcher.group(1))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * パッケージの名前から、モジュールの名前を取り出す。
     *
     * @param packageName パッケージの名前
     * @return モジュールの名前。モジュールのパッケージ（直下を含む）でなければ空
     */
    public static Optional<String> moduleName(String packageName) {
        Matcher matcher = MODULE_NAME.matcher(packageName);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /**
     * 生成したコード（OpenAPI の interface と型、jOOQ）のパッケージかを返す。
     *
     * @param packageName パッケージの名前
     * @return 生成したコードなら true
     */
    public static boolean isGenerated(String packageName) {
        return isInPackage(packageName, API_PACKAGE) || isInPackage(packageName, JOOQ_PACKAGE);
    }

    /**
     * パッケージの名前が、指定したパッケージそのものか、その下のパッケージかを返す。
     *
     * @param packageName パッケージの名前
     * @param root        パッケージ
     * @return パッケージそのもの、またはその下なら true
     */
    private static boolean isInPackage(String packageName, String root) {
        return packageName.equals(root) || packageName.startsWith(root + ".");
    }

    /**
     * クラスが、単体テストと統合テストが共有する testFixtures のクラスかを返す。
     *
     * @param javaClass クラス
     * @return ソースの場所が testFixtures なら true
     */
    private static boolean isTestFixture(JavaClass javaClass) {
        return javaClass.getSource().map(source -> isTestFixtureLocation(source.getUri().toString())).orElse(false);
    }

    /**
     * ソースの場所が testFixtures かを返す。単体テストの classpath には、ディレクトリでなく、test-fixtures の jar として入る。
     *
     * @param location クラスの場所の URI
     * @return testFixtures のディレクトリか jar なら true
     */
    private static boolean isTestFixtureLocation(String location) {
        return location.contains("/testFixtures/") || location.contains("-test-fixtures.jar");
    }

    /**
     * 絞り込んだ検査の対象が 1 件以上あることを確認する。<br />
     * 対象が 0 件の規則は、違反を見つけられないまま常に通るので、0 件なら説明を付けて失敗させる。
     *
     * @param targets     絞り込んだ検査の対象
     * @param description 対象の説明（失敗の原因に出す）
     */
    public static void requireNotEmpty(Collection<?> targets, String description) {
        assertThat(targets)
                .as("検査の対象が 0 件で、規則が常に通る状態になっている: %s", description)
                .isNotEmpty();
    }

    /**
     * 本番のクラスを、遅延の static で 1 回だけ読み込む入れ物。<br />
     * 外側のクラスを読み込んだだけでは、クラスファイルを読まない。
     */
    private static final class Holder {

        /**
         * {@code com.ecsite} の本番のクラス。<br />
         * 単体テストと統合テストが共有する testFixtures は、テストのコードなので除く。
         */
        private static final JavaClasses ALL = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(location -> !isTestFixtureLocation(location.asURI().toString()))
                .importPackages("com.ecsite");

        /**
         * 生成したコードを除いた本番のクラス
         */
        private static final JavaClasses HANDWRITTEN = ALL.that(
                DescribedPredicate.describe("handwritten", javaClass -> !isGenerated(javaClass.getPackageName())));

        /**
         * {@code com.ecsite.module} の本番のクラス
         */
        private static final JavaClasses MODULES = ALL.that(
                DescribedPredicate.describe("modules", javaClass -> javaClass.getPackageName().startsWith(MODULE_PACKAGE_PREFIX)));
    }

    /**
     * Spring Modulith のモジュールの一覧を、遅延の static で 1 回だけ作る入れ物。<br />
     * クラスファイルの読み込みと分けるのは、モジュールの一覧を使わない検査で作らないため。
     */
    private static final class ModulesHolder {

        /**
         * Spring Modulith のモジュールの一覧
         */
        private static final ApplicationModules APPLICATION_MODULES = ApplicationModules.of(EcSiteApplication.class, TEST_CLASSES);
    }
}
