package com.ecsite.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * モジュールの標準の構成の検査（docs/style/backend.md 第 1 節、spec 005 DC-27〜30）。<br />
 * 層の名前と依存の向き、注釈の置き場所、パッケージの大きさを、本番のクラスファイルから確かめる。
 * モジュールの境界（モジュールの間の依存）は {@code ModulithTest} が確かめる。
 */
class ModuleStructureTest {

    /**
     * モジュールのパッケージの接頭辞
     */
    private static final String MODULE_PACKAGE = "com.ecsite.module";

    /**
     * モジュールの直下に置いてよいパッケージ（層）
     */
    private static final Set<String> LAYERS = Set.of("api", "event", "presentation", "application", "domain", "infrastructure");

    /**
     * レベル A のモジュール（constitution 4.2。docs/modules の各設計書の「レベル」と合わせる）
     */
    private static final List<String> LEVEL_A_MODULES = List.of("tax", "catalog", "inventory", "order");

    /**
     * 1 つのパッケージの直下に置けるクラスの数の上限
     */
    private static final int MAX_CLASSES_PER_PACKAGE = 20;

    /**
     * モジュールのパッケージから、モジュールの名前と、モジュールの直下のパッケージの名前を取り出す形
     */
    private static final Pattern MODULE_SUBPACKAGE = Pattern.compile("^com\\.ecsite\\.module\\.([^.]+)\\.([^.]+)");

    /**
     * 本番のクラス（テストを除く）
     */
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.ecsite");

    @Test
    @DisplayName("モジュールの直下のパッケージは、標準の層の名前だけである")
    void modulesContainOnlyStandardLayers() {
        Set<String> unknown = CLASSES.stream()
                .map(JavaClass::getPackageName)
                .map(MODULE_SUBPACKAGE::matcher)
                .filter(Matcher::find)
                .filter(matcher -> !LAYERS.contains(matcher.group(2)))
                .map(Matcher::group)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(unknown).isEmpty();
    }

    @Test
    @DisplayName("domain は、同じモジュールのほかの層に依存しない")
    void domainDependsOnNoOtherLayer() {
        for (String module : modules()) {
            ArchRule rule = noClasses().that().resideInAPackage(layer(module, "domain"))
                    .should().dependOnClassesThat().resideInAnyPackage(
                            layer(module, "api"), layer(module, "presentation"),
                            layer(module, "application"), layer(module, "infrastructure"))
                    .allowEmptyShould(true);
            rule.check(CLASSES);
        }
    }

    @Test
    @DisplayName("レベル A の domain は、Spring と jOOQ に依存しない")
    void levelADomainIsFrameworkFree() {
        for (String module : LEVEL_A_MODULES) {
            ArchRule rule = noClasses().that().resideInAPackage(layer(module, "domain"))
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "org.jooq..");
            rule.check(CLASSES);
        }
    }

    @Test
    @DisplayName("application は、presentation と infrastructure に依存しない")
    void applicationDependsOnNeitherPresentationNorInfrastructure() {
        for (String module : modules()) {
            ArchRule rule = noClasses().that().resideInAPackage(layer(module, "application"))
                    .should().dependOnClassesThat().resideInAnyPackage(
                            layer(module, "presentation"), layer(module, "infrastructure"))
                    .allowEmptyShould(true);
            rule.check(CLASSES);
        }
    }

    @Test
    @DisplayName("presentation・api・event と infrastructure は、互いに依存しない")
    void entryLayersAndInfrastructureAreIndependent() {
        for (String module : modules()) {
            noClasses().that().resideInAnyPackage(layer(module, "presentation"), layer(module, "api"), layer(module, "event"))
                    .should().dependOnClassesThat().resideInAPackage(layer(module, "infrastructure"))
                    .allowEmptyShould(true)
                    .check(CLASSES);
            noClasses().that().resideInAPackage(layer(module, "infrastructure"))
                    .should().dependOnClassesThat().resideInAPackage(layer(module, "presentation"))
                    .allowEmptyShould(true)
                    .check(CLASSES);
        }
    }

    @Test
    @DisplayName("@Transactional は application と infrastructure だけに付ける")
    void transactionalOnlyInApplicationOrInfrastructure() {
        classes().that().areAnnotatedWith(Transactional.class)
                .should().resideInAnyPackage(MODULE_PACKAGE + ".*.application..", MODULE_PACKAGE + ".*.infrastructure..")
                .allowEmptyShould(true)
                .check(CLASSES);
        methods().that().areAnnotatedWith(Transactional.class)
                .should().beDeclaredInClassesThat()
                .resideInAnyPackage(MODULE_PACKAGE + ".*.application..", MODULE_PACKAGE + ".*.infrastructure..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    @DisplayName("@RestController は presentation だけに付ける")
    void restControllerOnlyInPresentation() {
        classes().that().areAnnotatedWith(RestController.class)
                .and().resideInAPackage(MODULE_PACKAGE + "..")
                .should().resideInAPackage(MODULE_PACKAGE + ".*.presentation..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("1 つのパッケージの直下のクラスは 20 を超えない")
    void packagesAreSmallEnough() {
        // 手で書いたパッケージごとに、入れ子でないクラスを数える（package-info は数えない）
        Map<String, Long> countByPackage = CLASSES.stream()
                .filter(javaClass -> !isGenerated(javaClass.getPackageName()))
                .filter(JavaClass::isTopLevelClass)
                .filter(javaClass -> !javaClass.getSimpleName().equals("package-info"))
                .collect(Collectors.groupingBy(JavaClass::getPackageName, TreeMap::new, Collectors.counting()));

        assertThat(countByPackage).allSatisfy((name, count) ->
                assertThat(count).as(name).isLessThanOrEqualTo(MAX_CLASSES_PER_PACKAGE));
    }

    @Test
    @DisplayName("他のモジュールを包む部品の、部品ごとのパッケージ（*dir）がない")
    void noDirectoryPackages() {
        Set<String> dirPackages = CLASSES.stream()
                .map(JavaClass::getPackageName)
                .filter(name -> name.startsWith(MODULE_PACKAGE))
                .filter(name -> name.endsWith("dir"))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(dirPackages).isEmpty();
    }

    /**
     * 生成したコード（OpenAPI の interface と型、jOOQ）のパッケージかを返す。
     *
     * @param packageName パッケージの名前
     * @return 生成したコードなら true
     */
    private static boolean isGenerated(String packageName) {
        return packageName.startsWith("com.ecsite.api.") || packageName.startsWith("com.ecsite.jooq");
    }

    /**
     * 本番のクラスがあるモジュールの名前を返す。
     *
     * @return モジュールの名前
     */
    private static Set<String> modules() {
        return CLASSES.stream()
                .map(JavaClass::getPackageName)
                .map(MODULE_SUBPACKAGE::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * モジュールの層のパッケージ（その下を含む）の ArchUnit の指定を返す。
     *
     * @param module モジュールの名前
     * @param layer  層の名前
     * @return パッケージの指定
     */
    private static String layer(String module, String layer) {
        return "%s.%s.%s..".formatted(MODULE_PACKAGE, module, layer);
    }
}
