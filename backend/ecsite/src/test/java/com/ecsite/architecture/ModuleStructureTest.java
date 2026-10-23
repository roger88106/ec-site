package com.ecsite.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.PackageMatcher;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
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
 * モジュールの標準の構成の検査（docs/rules/backend-coding.md 第 1 節、spec 005 DC-27〜30）。<br />
 * 層の名前と依存の向き、注釈の置き場所、パッケージの大きさを、本番のクラスファイルから確認する。
 * モジュールの境界（モジュールの間の依存）は {@code ModulithTest} が確認する。
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
     * レベル A のモジュール（バックエンドのアーキテクチャ 第 2 節。docs/design/modules の各設計書の「レベル」と合わせる）
     */
    private static final List<String> LEVEL_A_MODULES = List.of("tax", "catalog", "inventory", "order", "payment");

    /**
     * 1 つのパッケージの直下に置けるクラスの数の上限
     */
    private static final int MAX_CLASSES_PER_PACKAGE = 20;

    /**
     * モジュールのパッケージから、モジュールの名前と、モジュールの直下のパッケージの名前を取り出す形
     */
    private static final Pattern MODULE_SUBPACKAGE = Pattern.compile("^com\\.ecsite\\.module\\.([^.]+)\\.([^.]+)");

    /**
     * presentation の外に置く例外の {@code @RestController}（どのモジュールにも属さない GET /me）
     */
    private static final String ME_CONTROLLER = "com.ecsite.config.security.MeController";

    /**
     * 他のモジュールの api を、infrastructure.directory の外から使ってよい組。<br />
     * 組を足すときは、理由をコメントに書き、バックエンドの書き方 第 1.3 節の例外にも書く。
     */
    private static final List<AllowedApiUse> ALLOWED_API_USES = List.of(
            // 会員の ID は token から読むだけで、presentation が application へ引数で渡す。
            // 会員の窓口（port と実装）をモジュールごとに作ると、読み取りだけのために 3 組が増える
            new AllowedApiUse("com.ecsite.module.member.api.CurrentMemberIds", "com.ecsite.module.*.presentation.."));

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
        for (String module : modulesWith("domain")) {
            ArchRule rule = noClasses().that().resideInAPackage(layer(module, "domain"))
                    .should().dependOnClassesThat().resideInAnyPackage(
                            layer(module, "api"), layer(module, "presentation"),
                            layer(module, "application"), layer(module, "infrastructure"));
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
        for (String module : modulesWith("application")) {
            ArchRule rule = noClasses().that().resideInAPackage(layer(module, "application"))
                    .should().dependOnClassesThat().resideInAnyPackage(
                            layer(module, "presentation"), layer(module, "infrastructure"));
            rule.check(CLASSES);
        }
    }

    @Test
    @DisplayName("application は、api に依存しない（公開の API が application の view を変換する）")
    void applicationDoesNotDependOnApi() {
        noClasses().that().resideInAPackage(MODULE_PACKAGE + ".*.application..")
                .should().dependOnClassesThat().resideInAPackage(MODULE_PACKAGE + ".*.api..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("presentation と application.model は、application.port に依存しない（port の型を view にして渡す）")
    void presentationAndApplicationModelDoNotDependOnPort() {
        noClasses().that().resideInAnyPackage(MODULE_PACKAGE + ".*.presentation..", MODULE_PACKAGE + ".*.application.model..")
                .should().dependOnClassesThat().resideInAPackage(MODULE_PACKAGE + ".*.application.port..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("presentation・api・event と infrastructure は、互いに依存しない")
    void entryLayersAndInfrastructureAreIndependent() {
        for (String module : modulesWith("presentation", "api", "event")) {
            noClasses().that().resideInAnyPackage(layer(module, "presentation"), layer(module, "api"), layer(module, "event"))
                    .should().dependOnClassesThat().resideInAPackage(layer(module, "infrastructure"))
                    .check(CLASSES);
        }
        for (String module : modulesWith("infrastructure")) {
            noClasses().that().resideInAPackage(layer(module, "infrastructure"))
                    .should().dependOnClassesThat().resideInAPackage(layer(module, "presentation"))
                    .check(CLASSES);
        }
    }

    @Test
    @DisplayName("@Transactional は application と infrastructure だけに付ける")
    void transactionalOnlyInApplicationOrInfrastructure() {
        classes().that().areAnnotatedWith(Transactional.class)
                .should().resideInAnyPackage(MODULE_PACKAGE + ".*.application..", MODULE_PACKAGE + ".*.infrastructure..")
                .check(CLASSES);
        methods().that().areAnnotatedWith(Transactional.class)
                .should().beDeclaredInClassesThat()
                .resideInAnyPackage(MODULE_PACKAGE + ".*.application..", MODULE_PACKAGE + ".*.infrastructure..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("@RestController は presentation だけに付ける（MeController を除く）")
    void restControllerOnlyInPresentation() {
        classes().that().areAnnotatedWith(RestController.class)
                .and().resideInAPackage("com.ecsite..")
                .and().doNotHaveFullyQualifiedName(ME_CONTROLLER)
                .should().resideInAPackage(MODULE_PACKAGE + ".*.presentation..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("共通コードと設定は、モジュールに依存しない")
    void commonAndConfigDependOnNoModule() {
        noClasses().that().resideInAnyPackage("com.ecsite.common..", "com.ecsite.config..")
                .should().dependOnClassesThat().resideInAPackage(MODULE_PACKAGE + "..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("モジュールは、設定に依存しない")
    void modulesDependOnNoConfig() {
        noClasses().that().resideInAPackage(MODULE_PACKAGE + "..")
                .should().dependOnClassesThat().resideInAPackage("com.ecsite.config..")
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

    @Test
    @DisplayName("他のモジュールの api を使うのは infrastructure.directory だけである（許可の一覧を除く）")
    void otherModulesApiIsUsedOnlyByDirectories() {
        for (String module : modulesWith("api")) {
            classes().that().resideOutsideOfPackages(
                            "%s.%s..".formatted(MODULE_PACKAGE, module), MODULE_PACKAGE + ".*.infrastructure.directory..")
                    .should(notUseApiOf(module))
                    .check(CLASSES);
        }
    }

    @Test
    @DisplayName("api を使う許可の一覧は、全て本番のクラスで使われている")
    void everyAllowedApiUseIsUsed() {
        for (AllowedApiUse allowed : ALLOWED_API_USES) {
            boolean used = CLASSES.stream()
                    .filter(javaClass -> PackageMatcher.of(allowed.userPackage()).matches(javaClass.getPackageName()))
                    .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                    .anyMatch(dependency -> dependency.getTargetClass().getName().equals(allowed.apiType()));
            assertThat(used).as(allowed.apiType()).isTrue();
        }
    }

    /**
     * 他のモジュールの api を、許可の一覧にない使い方で使わない、という条件を返す。
     *
     * @param module api を持つモジュールの名前
     * @return 条件
     */
    private static ArchCondition<JavaClass> notUseApiOf(String module) {
        String apiPackage = "%s.%s.api".formatted(MODULE_PACKAGE, module);
        return new ArchCondition<>("%s の api を、許可の一覧にない使い方で使わない".formatted(module)) {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (target.getPackageName().equals(apiPackage) && !isAllowed(javaClass, target)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    /**
     * api の型の使い方が、許可の一覧にあるかを返す。
     *
     * @param user   使う側のクラス
     * @param target 使われる api の型
     * @return 許可の一覧にあれば true
     */
    private static boolean isAllowed(JavaClass user, JavaClass target) {
        return ALLOWED_API_USES.stream().anyMatch(allowed ->
                allowed.apiType().equals(target.getName())
                        && PackageMatcher.of(allowed.userPackage()).matches(user.getPackageName()));
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
     * 指定した層のどれかに、本番のクラスがあるモジュールの名前を返す。<br />
     * 層を持たないモジュールで繰り返すと、規則の対象が 0 件になって常に通るので、層を持つモジュールだけを返す。
     *
     * @param layers 層の名前
     * @return モジュールの名前
     */
    private static Set<String> modulesWith(String... layers) {
        Set<String> wanted = Set.of(layers);
        return CLASSES.stream()
                .map(JavaClass::getPackageName)
                .map(MODULE_SUBPACKAGE::matcher)
                .filter(Matcher::find)
                .filter(matcher -> wanted.contains(matcher.group(2)))
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

    /**
     * 他のモジュールの api を使ってよい組
     *
     * @param apiType     使われる api の型の完全修飾名
     * @param userPackage 使ってよい層のパッケージ（ArchUnit の指定）
     */
    private record AllowedApiUse(String apiType, String userPackage) {
    }
}
