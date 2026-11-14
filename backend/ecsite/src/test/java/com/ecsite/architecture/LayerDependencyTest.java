package com.ecsite.architecture;

import com.ecsite.ProductionClasses;
import com.ecsite.architecture.sample.layerdeps.module.sample.api.ApiToApplication;
import com.ecsite.architecture.sample.layerdeps.module.sample.api.ApiToPresentation;
import com.ecsite.architecture.sample.layerdeps.module.sample.application.ApplicationToApi;
import com.ecsite.architecture.sample.layerdeps.module.sample.application.SampleProperties;
import com.ecsite.architecture.sample.layerdeps.module.sample.application.SampleService;
import com.ecsite.architecture.sample.layerdeps.module.sample.application.port.SamplePort;
import com.ecsite.architecture.sample.layerdeps.module.sample.domain.DomainToApplication;
import com.ecsite.architecture.sample.layerdeps.module.sample.domain.SampleDomain;
import com.ecsite.architecture.sample.layerdeps.module.sample.extra.ExtraLayerClass;
import com.ecsite.architecture.sample.layerdeps.module.sample.infrastructure.InfrastructureToPort;
import com.ecsite.architecture.sample.layerdeps.module.sample.infrastructure.InfrastructureToService;
import com.ecsite.architecture.sample.layerdeps.module.sample.presentation.PresentationToPort;
import com.ecsite.architecture.sample.layerdeps.module.sample.presentation.SamplePresentation;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.ecsite.architecture.ArchRuleViolations.assertViolation;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * モジュールの中の層の依存の検査
 *
 * <p>根拠はバックエンドの書き方の「層とパッケージ」、バックエンドのアーキテクチャの「層とパッケージ」。
 * 同じモジュールの中の層の依存の向きを、本番のクラスファイルから確認する。
 * モジュールの境界（モジュールの間の依存）は {@code ModulithTest}、他のモジュールの api の使い方は {@code ModuleApiUseTest} が確認する。
 */
class LayerDependencyTest {

    // ============================== 定数 ==============================

    /**
     * クラスのパッケージを、モジュールのパッケージ、層、層の下のパッケージに分ける形
     *
     * <p>層の下のパッケージはなくてもよい。
     */
    private static final Pattern LAYERED_PACKAGE = Pattern.compile("^(.*\\.module\\.[^.]+)\\.([^.]+)(?:\\.(.*))?$");

    /**
     * 層ごとの、同じモジュールの中で依存してよい先
     *
     * <p>根拠はバックエンドのアーキテクチャの「層とパッケージ」の依存の表。
     * 層の中の自分自身への依存は、表に書かなくても許す。他のモジュールと共通コードへの依存は、この表の対象でない
     * （モジュールの間は {@code ModulithTest} と {@code ModuleApiUseTest} が確認する）。
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
            .that(DescribedPredicate.describe("are in a layer of a module", LayerDependencyTest::isLayered))
            .should(new DependOnlyOnTableCondition());

    /**
     * 層の依存の規則に合う例（表にある組だけ）のクラス
     */
    private static final Class<?>[] ALLOWED_LAYER_EXAMPLES = {
            ApiToApplication.class, SampleService.class, SamplePort.class, SampleProperties.class,
            InfrastructureToPort.class, SampleDomain.class};

    // ============================== テスト ==============================

    @Nested
    @DisplayName("同じモジュールの中の層の依存")
    class LayersInModule {

        @Test
        @DisplayName("層は、同じモジュールの中で、依存の表にない層とパッケージに依存しない")
        void layersDependOnlyOnAllowedLayers() {
            // 操作と確認：本番のモジュールのクラスに規則を当てる（対象が 0 件なら ArchUnit が失敗にする）
            LAYER_DEPENDENCY_RULE.check(ProductionClasses.modules());
        }

        @Test
        @DisplayName("規則は、表にある組の依存には当たらない")
        void ruleDoesNotMatchCombinationsInTable() {
            // 準備：表にある組だけで依存する例
            JavaClasses examples = new ClassFileImporter().importClasses(ALLOWED_LAYER_EXAMPLES);

            // 操作と確認：違反がない
            assertThat(LAYER_DEPENDENCY_RULE.evaluate(examples).hasViolation()).isFalse();
        }

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("com.ecsite.architecture.LayerDependencyTest#forbiddenLayerExamples")
        @DisplayName("規則は、表にない組の依存に当たる")
        void ruleMatchesCombinationOutsideTable(Class<?> origin, Class<?> target) {
            // 準備：表にある組の例に、表にない組で依存する例を 1 つ加える
            JavaClasses examples = new ClassFileImporter().importClasses(
                    Stream.concat(Stream.of(ALLOWED_LAYER_EXAMPLES), Stream.of(origin)).toArray(Class<?>[]::new));

            // 操作：規則を当てる
            List<String> details = LAYER_DEPENDENCY_RULE.evaluate(examples).getFailureReport().getDetails();

            // 確認：加えた例の依存だけが違反になる
            assertThat(details).hasSize(1);
            assertViolation(details, origin, target);
        }

        @Test
        @DisplayName("規則は、表にない層のクラスを、層の名前を付けた違反にする")
        void ruleMatchesLayerOutsideTable() {
            // 準備：表にない層（extra）のクラス
            JavaClasses examples = new ClassFileImporter().importClasses(ExtraLayerClass.class);

            // 操作：規則を当てる
            List<String> details = LAYER_DEPENDENCY_RULE.evaluate(examples).getFailureReport().getDetails();

            // 確認：クラスと層の名前を示す違反が 1 件
            assertThat(details).singleElement().asString()
                    .contains(ExtraLayerClass.class.getName())
                    .contains("依存の表にない層 extra");
        }
    }

    @Nested
    @DisplayName("層ごとの依存の制限")
    class LayerRestrictions {

        @Test
        @DisplayName("レベル A の domain は、Spring と jOOQ に依存しない")
        void levelADomainIsFrameworkFree() {
            // 操作と確認：レベル A のモジュールごとに、本番のクラスに規則を当てる
            for (String module : ProductionClasses.LEVEL_A_MODULES) {
                ArchRule rule = noClasses().that().resideInAPackage(layer(module, "domain"))
                        .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "org.jooq..");
                rule.check(ProductionClasses.all());
            }
        }

        @Test
        @DisplayName("presentation と application.model は、application.port に依存しない（port の型を view にして渡す）")
        void presentationAndApplicationModelDoNotDependOnPort() {
            // 操作と確認：本番のクラスに規則を当てる
            noClasses().that().resideInAnyPackage(
                            ProductionClasses.MODULE_PACKAGE + ".*.presentation..", ProductionClasses.MODULE_PACKAGE + ".*.application.model..")
                    .should().dependOnClassesThat().resideInAPackage(ProductionClasses.MODULE_PACKAGE + ".*.application.port..")
                    .check(ProductionClasses.all());
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 層の依存の規則に当たる例（表にない組）と、その依存先を返す。
     *
     * @return 依存元の例のクラス（層の組の名前を付ける）と依存先のクラス
     */
    static Stream<Arguments> forbiddenLayerExamples() {
        return Stream.of(
                Arguments.of(Named.of("api → presentation", ApiToPresentation.class), SamplePresentation.class),
                Arguments.of(Named.of("infrastructure → application の service", InfrastructureToService.class), SampleService.class),
                Arguments.of(Named.of("domain → application", DomainToApplication.class), SampleService.class),
                Arguments.of(Named.of("application → api", ApplicationToApi.class), ApiToApplication.class),
                Arguments.of(Named.of("presentation → application.port", PresentationToPort.class), SamplePort.class));
    }

    // ============================== 規則の補助 ==============================

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
     * クラスがある層の名前を返す。
     *
     * @param javaClass モジュールの層のクラス
     * @return 層の名前
     */
    private static String layerOf(JavaClass javaClass) {
        Matcher origin = LAYERED_PACKAGE.matcher(javaClass.getPackageName());
        if (!origin.matches()) {
            throw new IllegalArgumentException("モジュールの層のクラスでない: " + javaClass.getName());
        }
        return origin.group(2);
    }

    /**
     * クラスの、同じモジュールの中への依存のうち、層の依存の表にないものを返す。
     *
     * @param javaClass 依存元のクラス（表にある層のクラス）
     * @return 表にない依存
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
        return ProductionClasses.isInPackage(javaClass.getPackageName(), modulePackage);
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
     * モジュールの層のパッケージ（その下を含む）の ArchUnit の指定を返す。
     *
     * @param module モジュールの名前
     * @param layer  層の名前
     * @return パッケージの指定
     */
    private static String layer(String module, String layer) {
        return "%s.%s.%s..".formatted(ProductionClasses.MODULE_PACKAGE, module, layer);
    }

    /**
     * 層のクラスの、同じモジュールの中への依存が、依存の表にあるという条件
     *
     * <p>表にない層のクラスは、層の名前を付けて違反にする。
     */
    private static final class DependOnlyOnTableCondition extends ArchCondition<JavaClass> {

        /**
         * 条件を作る。
         */
        DependOnlyOnTableCondition() {
            super("depend only on the layers and packages in the dependency table");
        }

        /**
         * クラスの、同じモジュールの中への依存が表にあるかを確認する。
         *
         * @param javaClass 層のクラス
         * @param events    違反の記録
         */
        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            String layer = layerOf(javaClass);
            if (!ALLOWED_LAYER_DEPENDENCIES.containsKey(layer)) {
                events.add(SimpleConditionEvent.violated(javaClass,
                        "%s は、依存の表にない層 %s にある".formatted(javaClass.getName(), layer)));
                return;
            }
            for (Dependency dependency : forbiddenDependencies(javaClass)) {
                events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
            }
        }
    }

    /**
     * 層が依存してよい、同じモジュールの中の先
     *
     * <p>先はモジュールからの相対のパッケージで決め、クラスの注釈で絞ることもある。
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
            return ProductionClasses.isInPackage(relative, root);
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
}
