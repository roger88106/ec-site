package com.ecsite.architecture;

import com.ecsite.ProductionClasses;
import com.ecsite.architecture.layerdeps.module.sample.api.ApiToApplication;
import com.ecsite.architecture.layerdeps.module.sample.api.ApiToPresentation;
import com.ecsite.architecture.layerdeps.module.sample.application.SampleProperties;
import com.ecsite.architecture.layerdeps.module.sample.application.SampleService;
import com.ecsite.architecture.layerdeps.module.sample.application.port.SamplePort;
import com.ecsite.architecture.layerdeps.module.sample.domain.SampleDomain;
import com.ecsite.architecture.layerdeps.module.sample.infrastructure.InfrastructureToPort;
import com.ecsite.architecture.layerdeps.module.sample.infrastructure.InfrastructureToService;
import com.ecsite.architecture.layerdeps.module.sample.presentation.SamplePresentation;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.PackageMatcher;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
     * クラスのパッケージを、モジュールのパッケージ、層、層の下のパッケージに分ける形（層の下のパッケージはなくてもよい）
     */
    private static final Pattern LAYERED_PACKAGE = Pattern.compile("^(.*\\.module\\.[^.]+)\\.([^.]+)(?:\\.(.*))?$");

    /**
     * 層ごとの、同じモジュールの中で依存してよい先（バックエンドの書き方 第 1.1 節の表）。<br />
     * 層の中の自分自身への依存は、表に書かなくても許す。他のモジュールと共通コードへの依存は、この表の対象でない
     * （モジュールの間は {@code ModulithTest} と {@code otherModulesApiIsUsedOnlyByDirectories} が確認する）。
     * presentation の domain への依存は、エラーの定義と値の型に限るが、集約に依存しないことは
     * {@code ModuleRoleTest} が確認するので、ここでは domain の全体を許す。
     */
    private static final Map<String, List<AllowedTarget>> ALLOWED_LAYER_DEPENDENCIES = Map.of(
            "api", List.of(AllowedTarget.whole("application"), AllowedTarget.whole("domain")),
            "event", List.of(AllowedTarget.whole("application"), AllowedTarget.whole("domain")),
            "presentation", List.of(AllowedTarget.except("application", "port"), AllowedTarget.whole("domain")),
            "application", List.of(AllowedTarget.whole("domain"), AllowedTarget.whole("event")),
            "domain", List.of(AllowedTarget.whole("event")),
            "infrastructure", List.of(
                    AllowedTarget.only("application", "port", "model"),
                    AllowedTarget.configurationProperties("application"),
                    AllowedTarget.whole("domain")));

    /**
     * 層が、同じモジュールの中で、依存の表にない層とパッケージに依存しない規則
     */
    private static final ArchRule LAYER_DEPENDENCY_RULE = classes()
            .that(DescribedPredicate.describe("are in a layer of a module", ModuleStructureTest::isLayered))
            .should(new ArchCondition<JavaClass>("depend only on the layers and packages in the dependency table") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    for (Dependency dependency : forbiddenDependencies(javaClass)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            });

    /**
     * 層の依存の規則の検査の例（違反する例を含む）のクラス
     */
    private static final Class<?>[] LAYER_EXAMPLES = {
            ApiToApplication.class, ApiToPresentation.class, SamplePresentation.class, SampleService.class,
            SamplePort.class, SampleProperties.class, InfrastructureToPort.class, InfrastructureToService.class,
            SampleDomain.class};

    /**
     * 1 つのパッケージの直下に置けるクラスの数の上限
     */
    private static final int MAX_CLASSES_PER_PACKAGE = 20;

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

    @Test
    @DisplayName("モジュールの直下のパッケージは、標準の層の名前だけである")
    void modulesContainOnlyStandardLayers() {
        // com.ecsite.module.<モジュール>.<層> の 5 つ目の要素までを、層の名前の確認と報告に使う
        Set<String> unknown = ProductionClasses.all().stream()
                .map(JavaClass::getPackageName)
                .filter(packageName -> ProductionClasses.moduleName(packageName).isPresent())
                .map(packageName -> packageName.split("\\.", 6))
                .filter(parts -> parts.length >= 5)
                .filter(parts -> !LAYERS.contains(parts[4]))
                .map(parts -> String.join(".", parts[0], parts[1], parts[2], parts[3], parts[4]))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(unknown).isEmpty();
    }

    @Test
    @DisplayName("層は、同じモジュールの中で、依存の表にない層とパッケージに依存しない（対象は 1 件以上ある）")
    void layersDependOnlyOnAllowedLayers() {
        ProductionClasses.requireNotEmpty(layeredClasses(ProductionClasses.modules()), "モジュールの層のクラス");

        LAYER_DEPENDENCY_RULE.check(ProductionClasses.modules());
    }

    @Test
    @DisplayName("層の依存の規則は、表にある組には当たらず、api → presentation と infrastructure → application の service に当たる")
    void layerDependencyRuleMatchesCombinationsOutsideTable() {
        JavaClasses examples = new ClassFileImporter().importClasses(LAYER_EXAMPLES);

        assertThat(forbiddenDependencies(examples)).containsExactlyInAnyOrder(
                ApiToPresentation.class.getName() + " -> " + SamplePresentation.class.getName(),
                InfrastructureToService.class.getName() + " -> " + SampleService.class.getName());
    }

    @Test
    @DisplayName("レベル A の domain は、Spring と jOOQ に依存しない")
    void levelADomainIsFrameworkFree() {
        for (String module : ProductionClasses.LEVEL_A_MODULES) {
            ArchRule rule = noClasses().that().resideInAPackage(layer(module, "domain"))
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "org.jooq..");
            rule.check(ProductionClasses.all());
        }
    }

    @Test
    @DisplayName("presentation と application.model は、application.port に依存しない（port の型を view にして渡す）")
    void presentationAndApplicationModelDoNotDependOnPort() {
        noClasses().that().resideInAnyPackage(MODULE_PACKAGE + ".*.presentation..", MODULE_PACKAGE + ".*.application.model..")
                .should().dependOnClassesThat().resideInAPackage(MODULE_PACKAGE + ".*.application.port..")
                .check(ProductionClasses.all());
    }

    @Test
    @DisplayName("@Transactional は application と infrastructure だけに付ける")
    void transactionalOnlyInApplicationOrInfrastructure() {
        classes().that().areAnnotatedWith(Transactional.class)
                .should().resideInAnyPackage(MODULE_PACKAGE + ".*.application..", MODULE_PACKAGE + ".*.infrastructure..")
                .check(ProductionClasses.all());
        methods().that().areAnnotatedWith(Transactional.class)
                .should().beDeclaredInClassesThat()
                .resideInAnyPackage(MODULE_PACKAGE + ".*.application..", MODULE_PACKAGE + ".*.infrastructure..")
                .check(ProductionClasses.all());
    }

    @Test
    @DisplayName("@RestController は presentation だけに付ける（MeController を除く）")
    void restControllerOnlyInPresentation() {
        classes().that().areAnnotatedWith(RestController.class)
                .and().resideInAPackage("com.ecsite..")
                .and().doNotHaveFullyQualifiedName(ME_CONTROLLER)
                .should().resideInAPackage(MODULE_PACKAGE + ".*.presentation..")
                .check(ProductionClasses.all());
    }

    @Test
    @DisplayName("共通コードと設定は、モジュールに依存しない")
    void commonAndConfigDependOnNoModule() {
        noClasses().that().resideInAnyPackage("com.ecsite.common..", "com.ecsite.config..")
                .should().dependOnClassesThat().resideInAPackage(MODULE_PACKAGE + "..")
                .check(ProductionClasses.all());
    }

    @Test
    @DisplayName("共通コードは、設定に依存しない")
    void commonDependsOnNoConfig() {
        noClasses().that().resideInAPackage("com.ecsite.common..")
                .should().dependOnClassesThat().resideInAPackage("com.ecsite.config..")
                .check(ProductionClasses.all());
    }

    @Test
    @DisplayName("モジュールは、設定に依存しない")
    void modulesDependOnNoConfig() {
        noClasses().that().resideInAPackage(MODULE_PACKAGE + "..")
                .should().dependOnClassesThat().resideInAPackage("com.ecsite.config..")
                .check(ProductionClasses.all());
    }

    @Test
    @DisplayName("1 つのパッケージの直下のクラスは 20 を超えない")
    void packagesAreSmallEnough() {
        // 手で書いたパッケージごとに、入れ子でないクラスを数える（package-info は数えない）
        Map<String, Long> countByPackage = ProductionClasses.all().stream()
                .filter(javaClass -> !ProductionClasses.isGenerated(javaClass.getPackageName()))
                .filter(JavaClass::isTopLevelClass)
                .filter(javaClass -> !javaClass.getSimpleName().equals("package-info"))
                .collect(Collectors.groupingBy(JavaClass::getPackageName, TreeMap::new, Collectors.counting()));

        assertThat(countByPackage).allSatisfy((name, count) ->
                assertThat(count).as(name).isLessThanOrEqualTo(MAX_CLASSES_PER_PACKAGE));
    }

    @Test
    @DisplayName("他のモジュールを包む部品の、部品ごとのパッケージ（*dir）がない")
    void noDirectoryPackages() {
        Set<String> dirPackages = ProductionClasses.all().stream()
                .map(JavaClass::getPackageName)
                .filter(name -> name.startsWith(MODULE_PACKAGE))
                .filter(name -> name.endsWith("dir"))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(dirPackages).isEmpty();
    }

    @Test
    @DisplayName("他のモジュールの api を使うのは infrastructure.directory だけである（許可の一覧を除く）")
    void otherModulesApiIsUsedOnlyByDirectories() {
        for (String module : ProductionClasses.modulesWith("api")) {
            classes().that().resideOutsideOfPackages(
                            "%s.%s..".formatted(MODULE_PACKAGE, module), MODULE_PACKAGE + ".*.infrastructure.directory..")
                    .should(notUseApiOf(module))
                    .check(ProductionClasses.all());
        }
    }

    @Test
    @DisplayName("api を使う許可の一覧は、全て本番のクラスで使われている")
    void everyAllowedApiUseIsUsed() {
        for (AllowedApiUse allowed : ALLOWED_API_USES) {
            boolean used = ProductionClasses.all().stream()
                    .filter(javaClass -> PackageMatcher.of(allowed.userPackage()).matches(javaClass.getPackageName()))
                    .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                    .anyMatch(dependency -> dependency.getTargetClass().getName().equals(allowed.apiType()));
            assertThat(used).as(allowed.apiType()).isTrue();
        }
    }

    /**
     * クラスが、モジュールの層（またはその下）のパッケージにあるかを返す。
     *
     * @param javaClass クラス
     * @return モジュールの層のパッケージにあれば true
     */
    private static boolean isLayered(JavaClass javaClass) {
        return LAYERED_PACKAGE.matcher(javaClass.getPackageName()).matches();
    }

    /**
     * 規則の対象になる、モジュールの層のクラスを返す。
     *
     * @param classes 調べるクラス
     * @return 層のパッケージのクラス
     */
    private static List<JavaClass> layeredClasses(JavaClasses classes) {
        return classes.stream().filter(ModuleStructureTest::isLayered).toList();
    }

    /**
     * 層の依存の表にない依存を、「依存元 -> 依存先」の形で返す。
     *
     * @param classes 調べるクラス
     * @return 表にない依存（名前の順）
     */
    private static List<String> forbiddenDependencies(JavaClasses classes) {
        return layeredClasses(classes).stream()
                .flatMap(javaClass -> forbiddenDependencies(javaClass).stream())
                .map(dependency -> dependency.getOriginClass().getName() + " -> " + dependency.getTargetClass().getName())
                .sorted()
                .toList();
    }

    /**
     * クラスの、同じモジュールの中への依存のうち、層の依存の表にないものを返す。
     *
     * @param javaClass 依存元のクラス
     * @return 表にない依存。モジュールの層のクラスでなければ空
     */
    private static List<Dependency> forbiddenDependencies(JavaClass javaClass) {
        Matcher origin = LAYERED_PACKAGE.matcher(javaClass.getPackageName());
        if (!origin.matches()) {
            return List.of();
        }
        String modulePackage = origin.group(1);
        String layer = origin.group(2);
        List<AllowedTarget> allowed = ALLOWED_LAYER_DEPENDENCIES.get(layer);
        return javaClass.getDirectDependenciesFromSelf().stream()
                .filter(dependency -> isInModule(dependency.getTargetClass(), modulePackage))
                .filter(dependency -> {
                    String relative = relativePackage(dependency.getTargetClass(), modulePackage);
                    return !AllowedTarget.isInside(relative, layer)
                            && allowed.stream().noneMatch(target -> target.allows(relative, dependency.getTargetClass()));
                })
                .toList();
    }

    /**
     * クラスが、指定したモジュールのパッケージ（直下を含む）にあるかを返す。
     *
     * @param javaClass     クラス
     * @param modulePackage モジュールのパッケージの名前
     * @return モジュールの中なら true
     */
    private static boolean isInModule(JavaClass javaClass, String modulePackage) {
        return javaClass.getPackageName().equals(modulePackage) || javaClass.getPackageName().startsWith(modulePackage + ".");
    }

    /**
     * モジュールの中のクラスの、モジュールからの相対のパッケージの名前を返す。
     *
     * @param javaClass     モジュールの中のクラス
     * @param modulePackage モジュールのパッケージの名前
     * @return 相対のパッケージの名前（例：{@code application.port}）。モジュールの直下なら空文字
     */
    private static String relativePackage(JavaClass javaClass, String modulePackage) {
        String name = javaClass.getPackageName();
        return name.length() > modulePackage.length() ? name.substring(modulePackage.length() + 1) : "";
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
     * 層が依存してよい、同じモジュールの中の先。<br />
     * 先はモジュールからの相対のパッケージで決め、クラスの注釈で絞ることもある。
     *
     * @param matches 相対のパッケージの名前と、依存先のクラスから、依存してよいかを返す関数
     */
    private record AllowedTarget(BiPredicate<String, JavaClass> matches) {

        /**
         * 層の全体（その下のパッケージを含む）に依存してよい先を返す。
         *
         * @param layer 層の名前
         * @return 依存してよい先
         */
        static AllowedTarget whole(String layer) {
            return new AllowedTarget((relative, target) -> isInside(relative, layer));
        }

        /**
         * 層の全体のうち、指定した下のパッケージを除いたものに依存してよい先を返す。
         *
         * @param layer    層の名前
         * @param excluded 除く下のパッケージの名前
         * @return 依存してよい先
         */
        static AllowedTarget except(String layer, String excluded) {
            return new AllowedTarget((relative, target) ->
                    isInside(relative, layer) && !isInside(relative, layer + "." + excluded));
        }

        /**
         * 層の、指定した下のパッケージだけに依存してよい先を返す。
         *
         * @param layer       層の名前
         * @param subpackages 依存してよい下のパッケージの名前
         * @return 依存してよい先
         */
        static AllowedTarget only(String layer, String... subpackages) {
            return new AllowedTarget((relative, target) ->
                    Stream.of(subpackages).anyMatch(subpackage -> isInside(relative, layer + "." + subpackage)));
        }

        /**
         * 層の、{@code @ConfigurationProperties} を付けたクラス（設定の値の型）だけに依存してよい先を返す。
         *
         * @param layer 層の名前
         * @return 依存してよい先
         */
        static AllowedTarget configurationProperties(String layer) {
            return new AllowedTarget((relative, target) ->
                    isInside(relative, layer) && isConfigurationProperties(target));
        }

        /**
         * 依存してよいかを返す。
         *
         * @param relative 依存先のモジュールからの相対のパッケージの名前
         * @param target   依存先のクラス
         * @return 依存してよければ true
         */
        boolean allows(String relative, JavaClass target) {
            return matches.test(relative, target);
        }

        /**
         * 相対のパッケージが、指定したパッケージそのものか、その下かを返す。
         *
         * @param relative 相対のパッケージの名前
         * @param root     パッケージの名前
         * @return パッケージそのもの、またはその下なら true
         */
        static boolean isInside(String relative, String root) {
            return relative.equals(root) || relative.startsWith(root + ".");
        }

        /**
         * クラスか、それを囲むクラスに {@code @ConfigurationProperties} が付いているかを返す。
         *
         * @param target クラス
         * @return 付いていれば true
         */
        private static boolean isConfigurationProperties(JavaClass target) {
            for (JavaClass current = target; current != null; current = current.getEnclosingClass().orElse(null)) {
                if (current.isAnnotatedWith(ConfigurationProperties.class)) {
                    return true;
                }
            }
            return false;
        }
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
