package com.ecsite.architecture;

import com.ecsite.architecture.sample.jooquse.module.sample.application.ApplicationUsesJooq;
import com.ecsite.architecture.sample.jooquse.module.sample.infrastructure.persistence.PersistenceUsesJooq;
import com.ecsite.support.ProductionClasses;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * schema の持ち主の検査
 *
 * <p>根拠はバックエンドのアーキテクチャの「永続化」。
 * 各モジュールが自分の schema の jOOQ の生成クラスだけを使うことを、本番のクラスファイルから確認する。
 * jOOQ の生成コード（{@code com.ecsite.jooq.<schema>}）はモジュールのパッケージの外にあるので、
 * {@code ModulithTest} では他のモジュールのテーブルの読み書きを検出できない。
 */
class SchemaOwnershipTest {

    // ============================== 定数 ==============================

    /**
     * jOOQ を、infrastructure/persistence の外から使ってよいクラス
     *
     * <p>追加するときは、理由をコメントに書き、バックエンドの書き方の「標準の構成」の例外の表にも書く。
     */
    private static final List<String> JOOQ_EXCEPTIONS = List.of(
            // mock の物流サービスの画面だけが使う問い合わせで、mock を無効にすると Bean が作られない。
            // persistence は port の実装の置き場所なので、本番で使わないクラスを置かない
            ProductionClasses.MODULE_PACKAGE_PREFIX + "shipping.infrastructure.mock.MockShipmentQuery");

    /**
     * モジュールの名前から、そのモジュールが持つ schema の名前への対応
     *
     * <p>schema を持たないモジュールは空にする。
     * common の schema（イベントの永続化の表）は Spring Modulith が JDBC で使うので、どのモジュールにも割り当てない。
     */
    private static final Map<String, Optional<String>> SCHEMA_BY_MODULE = Map.ofEntries(
            Map.entry("cart", Optional.of("cart")),
            Map.entry("catalog", Optional.of("catalog")),
            Map.entry("inventory", Optional.of("inventory")),
            Map.entry("media", Optional.of("media")),
            Map.entry("member", Optional.of("member")),
            Map.entry("order", Optional.of("ordering")),
            Map.entry("payment", Optional.of("payment")),
            Map.entry("shipping", Optional.of("shipping")),
            Map.entry("notification", Optional.of("notification")),
            Map.entry("reporting", Optional.of("reporting")),
            Map.entry("region", Optional.of("region")),
            Map.entry("storefront", Optional.empty()),
            Map.entry("tax", Optional.of("tax")));

    /**
     * どのモジュールにも属さない schema
     *
     * <p>common の schema（イベントの永続化の表）は Spring Modulith が JDBC で使うので、どのモジュールにも割り当てない。
     */
    private static final Set<String> SCHEMAS_WITHOUT_MODULE = Set.of("common");

    // ============================== テスト ==============================

    @Test
    @DisplayName("Gradle の jOOQ の schema の一覧は、モジュールと schema の対応と過不足がない")
    void jooqSchemasMatchSchemaByModule() {
        // 準備：期待する schema（モジュールの schema とモジュールに属さない schema）
        Set<String> expected = SCHEMA_BY_MODULE.values().stream()
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(TreeSet::new));
        expected.addAll(SCHEMAS_WITHOUT_MODULE);

        // 操作：Gradle が渡す jOOQ の schema の一覧
        List<String> actual = gradleJooqSchemas();

        // 確認：過不足がない
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    @DisplayName("すべてのモジュールが、schema の対応の表にある")
    void everyModuleHasSchemaEntry() {
        // 準備：本番のクラスのモジュールの名前
        Set<String> modules = ProductionClasses.all().stream()
                .map(JavaClass::getPackageName)
                .map(ProductionClasses::moduleName)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(TreeSet::new));

        // 確認：表のモジュールと過不足がない
        assertThat(SCHEMA_BY_MODULE.keySet()).containsExactlyInAnyOrderElementsOf(modules);
    }

    @Test
    @DisplayName("モジュールは、自分の schema 以外の jOOQ の生成クラスに依存しない")
    void modulesUseOnlyOwnSchema() {
        // 操作と確認：モジュールごとに、本番のクラスに規則を当てる
        SCHEMA_BY_MODULE.forEach((module, schema) -> {
            DescribedPredicate<JavaClass> otherSchema = schema
                    .map(name -> resideInAPackage(ProductionClasses.JOOQ_PACKAGE + "..")
                            .and(DescribedPredicate.not(resideInAPackage(ProductionClasses.JOOQ_PACKAGE + "." + name + ".."))))
                    .orElseGet(() -> resideInAPackage(ProductionClasses.JOOQ_PACKAGE + ".."));
            noClasses().that().resideInAPackage(ProductionClasses.MODULE_PACKAGE_PREFIX + module + "..")
                    .should().dependOnClassesThat(otherSchema.as(schema.map(name -> "are jOOQ classes outside schema " + name).orElse("are jOOQ classes")))
                    .check(ProductionClasses.all());
        });
    }

    @Test
    @DisplayName("モジュールの外の手書きのクラスは、jOOQ の生成クラスに依存しない")
    void nonModuleClassesUseNoSchema() {
        // 操作と確認：本番のクラスに規則を当てる
        noClasses().that().resideOutsideOfPackages(ProductionClasses.MODULE_PACKAGE + "..", ProductionClasses.JOOQ_PACKAGE + "..")
                .should().dependOnClassesThat().resideInAPackage(ProductionClasses.JOOQ_PACKAGE + "..")
                .check(ProductionClasses.all());
    }

    @Test
    @DisplayName("モジュールの jOOQ は、infrastructure/persistence だけが使う（mock の出荷の問い合わせを除く）")
    void jooqIsUsedOnlyInPersistence() {
        // 操作と確認：本番のクラスに規則を当てる
        jooqPlacementRule(ProductionClasses.MODULE_PACKAGE + "..", ProductionClasses.MODULE_PACKAGE + ".*.infrastructure.persistence..")
                .check(ProductionClasses.all());
    }

    @Test
    @DisplayName("jOOQ の置き場所の規則は、application 層で jOOQ を使う例に当たる")
    void jooqPlacementRuleMatchesApplicationExample() {
        // 操作：application 層と persistence で jOOQ を使う例に、規則を当てる
        List<String> details = jooqExampleDetails();

        // 確認：application 層の例が違反になる
        assertThat(details).anyMatch(detail -> detail.contains(ApplicationUsesJooq.class.getName()));
    }

    @Test
    @DisplayName("jOOQ の置き場所の規則は、persistence で jOOQ を使う例には当たらない")
    void jooqPlacementRuleDoesNotMatchPersistenceExample() {
        // 操作：application 層と persistence で jOOQ を使う例に、規則を当てる
        List<String> details = jooqExampleDetails();

        // 確認：persistence の例は違反にならない
        ArchRuleViolations.assertNoViolation(details, PersistenceUsesJooq.class);
    }

    @Test
    @DisplayName("jOOQ の置き場所の例外は、本番のクラスで jOOQ を使っている")
    void jooqExceptionsAreUsed() {
        // 操作と確認：例外のクラスごとに、本番のクラスが jOOQ に依存している
        for (String exception : JOOQ_EXCEPTIONS) {
            boolean used = ProductionClasses.all().stream()
                    .filter(javaClass -> ProductionClasses.isOrNestedIn(javaClass, exception))
                    .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                    .anyMatch(dependency -> dependency.getTargetClass().getPackageName().startsWith("org.jooq"));
            assertThat(used).as(exception).isTrue();
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * application 層と persistence で jOOQ を使う例に、例のパッケージに置き換えた置き場所の規則を当てた違反の詳細を返す。
     *
     * @return 違反の詳細
     */
    private static List<String> jooqExampleDetails() {
        var examples = new ClassFileImporter().importClasses(ApplicationUsesJooq.class, PersistenceUsesJooq.class);
        return jooqPlacementRule("com.ecsite.architecture.sample.jooquse.module..",
                "com.ecsite.architecture.sample.jooquse.module.*.infrastructure.persistence..")
                .evaluate(examples).getFailureReport().getDetails();
    }

    /**
     * Gradle が system property で渡す、jOOQ の schema の一覧を返す。
     *
     * @return schema の名前の一覧
     * @throws IllegalStateException system property がないとき
     */
    private static List<String> gradleJooqSchemas() {
        String schemas = System.getProperty("jooq.schemas");
        if (schemas == null) {
            throw new IllegalStateException("system property jooq.schemas がない（Gradle の test の task から流す）");
        }
        return List.of(schemas.split(","));
    }

    // ============================== 規則の補助 ==============================

    /**
     * jOOQ（{@code org.jooq} と生成したコード）を使えるクラスを、persistence のパッケージと例外の一覧のクラスだけにする規則を作る。
     *
     * @param modulePackage      対象にするモジュールのパッケージ（例：com.ecsite.module..）
     * @param persistencePackage persistence のパッケージ（例：com.ecsite.module.*.infrastructure.persistence..）
     * @return 規則
     */
    private static ArchRule jooqPlacementRule(String modulePackage, String persistencePackage) {
        return noClasses().that().resideInAPackage(modulePackage)
                .and().resideOutsideOfPackage(persistencePackage)
                .and(DescribedPredicate.not(DescribedPredicate.describe("listed in the exceptions",
                        javaClass -> JOOQ_EXCEPTIONS.stream().anyMatch(exception -> ProductionClasses.isOrNestedIn(javaClass, exception)))))
                .should().dependOnClassesThat().resideInAnyPackage("org.jooq..", ProductionClasses.JOOQ_PACKAGE + "..");
    }
}
