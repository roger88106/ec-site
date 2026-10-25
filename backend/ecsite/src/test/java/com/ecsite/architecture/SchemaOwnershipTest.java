package com.ecsite.architecture;

import com.ecsite.ProductionClasses;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * schema の持ち主の検査（バックエンドのアーキテクチャ 第 6.1 節）。<br />
 * 各モジュールが自分の schema の jOOQ の生成クラスだけを使うことを、本番のクラスファイルから確認する。
 * jOOQ の生成コード（{@code com.ecsite.jooq.<schema>}）はモジュールのパッケージの外にあるので、
 * {@code ModulithTest} では他のモジュールのテーブルの読み書きを検出できない。
 */
class SchemaOwnershipTest {

    /**
     * jOOQ の生成コードのパッケージ（build.gradle.kts の packageName）
     */
    private static final String JOOQ_PACKAGE = "com.ecsite.jooq";

    /**
     * モジュールの名前から、そのモジュールが持つ schema の名前への対応。
     * schema を持たないモジュールは空にする。
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

    @Test
    @DisplayName("すべてのモジュールが、schema の対応の表にある")
    void everyModuleHasSchemaEntry() {
        Set<String> modules = ProductionClasses.all().stream()
                .map(JavaClass::getPackageName)
                .map(ProductionClasses::moduleName)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(SCHEMA_BY_MODULE.keySet()).containsExactlyInAnyOrderElementsOf(modules);
    }

    @Test
    @DisplayName("モジュールは、自分の schema 以外の jOOQ の生成クラスに依存しない")
    void modulesUseOnlyOwnSchema() {
        SCHEMA_BY_MODULE.forEach((module, schema) -> {
            DescribedPredicate<JavaClass> otherSchema = schema
                    .map(name -> resideInAPackage(JOOQ_PACKAGE + "..")
                            .and(DescribedPredicate.not(resideInAPackage(JOOQ_PACKAGE + "." + name + ".."))))
                    .orElseGet(() -> resideInAPackage(JOOQ_PACKAGE + ".."));
            noClasses().that().resideInAPackage("com.ecsite.module." + module + "..")
                    .should().dependOnClassesThat(otherSchema.as(schema.map(name -> "are jOOQ classes outside schema " + name).orElse("are jOOQ classes")))
                    .check(ProductionClasses.all());
        });
    }

    @Test
    @DisplayName("モジュールの外の手書きのクラスは、jOOQ の生成クラスに依存しない")
    void nonModuleClassesUseNoSchema() {
        noClasses().that().resideOutsideOfPackages("com.ecsite.module..", JOOQ_PACKAGE + "..")
                .should().dependOnClassesThat().resideInAPackage(JOOQ_PACKAGE + "..")
                .check(ProductionClasses.all());
    }
}
