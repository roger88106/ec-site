package com.ecsite.architecture;

import com.ecsite.ProductionClasses;
import com.ecsite.architecture.commondeps.sample.common.CommonToConfig;
import com.ecsite.architecture.commondeps.sample.config.SampleConfig;
import com.ecsite.architecture.layerdeps.module.sample.api.ApiToApplication;
import com.ecsite.architecture.layerdeps.module.sample.api.ApiToPresentation;
import com.ecsite.architecture.layerdeps.module.sample.application.ApplicationToApi;
import com.ecsite.architecture.layerdeps.module.sample.application.SampleProperties;
import com.ecsite.architecture.layerdeps.module.sample.application.SampleService;
import com.ecsite.architecture.layerdeps.module.sample.application.port.SamplePort;
import com.ecsite.architecture.layerdeps.module.sample.domain.DomainToApplication;
import com.ecsite.architecture.layerdeps.module.sample.domain.SampleDomain;
import com.ecsite.architecture.layerdeps.module.sample.extra.ExtraLayerClass;
import com.ecsite.architecture.layerdeps.module.sample.infrastructure.InfrastructureToPort;
import com.ecsite.architecture.layerdeps.module.sample.infrastructure.InfrastructureToService;
import com.ecsite.architecture.layerdeps.module.sample.presentation.PresentationToPort;
import com.ecsite.architecture.layerdeps.module.sample.presentation.SamplePresentation;
import com.ecsite.architecture.tokenaccess.sample.application.ApplicationUsesCurrentMember;
import com.ecsite.architecture.tokenaccess.sample.infrastructure.directory.DirectoryUsesCurrentMemberIds;
import com.ecsite.architecture.tokenaccess.sample.presentation.PresentationUsesCurrentMember;
import com.ecsite.common.security.CurrentMember;
import com.ecsite.common.security.CurrentOperator;
import com.ecsite.common.security.CurrentToken;
import com.ecsite.contract.Contracts;
import com.ecsite.module.member.api.CurrentMemberIds;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * モジュールの標準の構成の検査（バックエンドの書き方の「層とパッケージ」）。<br />
 * 層の名前と依存の向き、注釈の置き場所、パッケージの大きさを、本番のクラスファイルから確認する。
 * モジュールの境界（モジュールの間の依存）は {@code ModulithTest} が確認する。
 */
class ModuleStructureTest {

    /**
     * モジュールの直下に置いてよいパッケージ（層）
     */
    private static final Set<String> LAYERS = Set.of("api", "event", "presentation", "application", "domain", "infrastructure");

    /**
     * クラスのパッケージを、モジュールのパッケージ、層、層の下のパッケージに分ける形（層の下のパッケージはなくてもよい）
     */
    private static final Pattern LAYERED_PACKAGE = Pattern.compile("^(.*\\.module\\.[^.]+)\\.([^.]+)(?:\\.(.*))?$");

    /**
     * 層ごとの、同じモジュールの中で依存してよい先（バックエンドの書き方の「標準の構成」の表）。<br />
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
            .should(new DependOnlyOnTableCondition());

    /**
     * 層の依存の規則に合う例（表にある組だけ）のクラス
     */
    private static final Class<?>[] ALLOWED_LAYER_EXAMPLES = {
            ApiToApplication.class, SampleService.class, SamplePort.class, SampleProperties.class,
            InfrastructureToPort.class, SampleDomain.class};

    /**
     * 層の依存の規則に当たる例（表にない組）のクラス
     */
    private static final Class<?>[] FORBIDDEN_LAYER_EXAMPLES = {
            ApiToPresentation.class, InfrastructureToService.class, DomainToApplication.class,
            ApplicationToApi.class, PresentationToPort.class};

    /**
     * 1 つのパッケージの直下に置けるクラスの数の上限
     */
    private static final int MAX_CLASSES_PER_PACKAGE = 20;

    /**
     * 1 つのパッケージの直下のクラスの数の目安。超えて分けないパッケージは、バックエンドの書き方の「パッケージの大きさ」の表に理由を書く
     */
    private static final int GUIDELINE_CLASSES_PER_PACKAGE = 10;

    /**
     * バックエンドの書き方（Gradle が system property で渡すリポジトリのルートから）
     */
    private static final Path RULES_BACKEND_CODING = Contracts.path("docs/05-開発規則/backend-coding.md");

    /**
     * バックエンドの書き方で、パッケージの大きさの表がある節の見出しの始まり
     */
    private static final String PACKAGE_SIZE_HEADING = "### 1.4 ";

    /**
     * パッケージの大きさの表の行（1 列目がバッククォートで囲んだパッケージの名前、2 列目が数）
     */
    private static final Pattern TABLE_ROW = Pattern.compile("^\\|\\s*`([^`]+)`\\s*\\|\\s*(\\d+)\\s*\\|.*$");

    /**
     * presentation の外に置く例外の {@code @RestController}（どのモジュールにも属さない GET /me）
     */
    private static final String ME_CONTROLLER = "com.ecsite.config.security.MeController";

    /**
     * 他のモジュールの api を、infrastructure.directory の外から使ってよい組。<br />
     * 組を追加するときは、理由をコメントに書き、バックエンドの書き方の「他のモジュールは腐敗防止層で包む」の例外にも書く。
     */
    private static final List<AllowedApiUse> ALLOWED_API_USES = List.of(
            // 会員の ID は token から読むだけで、presentation が application へ引数で渡す。
            // 会員の窓口（port と実装）をモジュールごとに作ると、読み取りだけのために 3 組が増える
            new AllowedApiUse(CurrentMemberIds.class.getName(), ProductionClasses.MODULE_PACKAGE + ".*.presentation.."));

    /**
     * token の取り出し（token、会員、運営者、会員 ID）のクラスの名前。<br />
     * 会員 ID の取り出し（CurrentMemberIds）を含める。他のモジュールの api の規則は infrastructure.directory からの利用を除くので、
     * それだけでは directory を経て application 層が要求の会員 ID を得る形を防げないため。
     */
    private static final Set<String> TOKEN_ACCESSORS = Set.of(
            CurrentToken.class.getName(), CurrentMember.class.getName(), CurrentOperator.class.getName(),
            CurrentMemberIds.class.getName());

    /**
     * token の取り出しを、presentation 層と common.security の外から使ってよいクラス。<br />
     * 追加するときは、理由をコメントに書き、バックエンドの書き方の「標準の構成」の例外の表にも書く。
     */
    private static final Set<String> TOKEN_ACCESS_EXCEPTIONS = Set.of(
            // 他のモジュールの presentation に会員の ID を渡す公開の API。自身も取り出しの対象（TOKEN_ACCESSORS）で、呼び出し元は presentation に限る
            CurrentMemberIds.class.getName(),
            // モジュールに属さない API の入口で、役割は presentation 層と同じ
            ME_CONTROLLER);

    @Test
    @DisplayName("モジュールの直下のパッケージは、標準の層の名前だけである")
    void modulesContainOnlyStandardLayers() {
        List<String> modulePackages = ProductionClasses.all().stream()
                .map(JavaClass::getPackageName)
                .filter(packageName -> ProductionClasses.moduleName(packageName).isPresent())
                .toList();
        ProductionClasses.requireNotEmpty(modulePackages, "モジュールのクラスのパッケージ");

        // com.ecsite.module.<モジュール>.<層> の 5 つ目の要素までを、層の名前の確認と報告に使う
        Set<String> unknown = modulePackages.stream()
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
    @DisplayName("層の依存の規則は、表にある組には当たらない")
    void layerDependencyRuleDoesNotMatchCombinationsInTable() {
        JavaClasses examples = new ClassFileImporter().importClasses(ALLOWED_LAYER_EXAMPLES);

        assertThat(LAYER_DEPENDENCY_RULE.evaluate(examples).hasViolation()).isFalse();
    }

    @Test
    @DisplayName("層の依存の規則は、表にない組（api → presentation、infrastructure → service、domain → application、application → api、presentation → port）に当たる")
    void layerDependencyRuleMatchesCombinationsOutsideTable() {
        JavaClasses examples = new ClassFileImporter().importClasses(Stream.concat(
                Stream.of(ALLOWED_LAYER_EXAMPLES), Stream.of(FORBIDDEN_LAYER_EXAMPLES)).toArray(Class<?>[]::new));

        List<String> details = LAYER_DEPENDENCY_RULE.evaluate(examples).getFailureReport().getDetails();

        assertThat(details).hasSize(5);
        assertViolation(details, ApiToPresentation.class, SamplePresentation.class);
        assertViolation(details, InfrastructureToService.class, SampleService.class);
        assertViolation(details, DomainToApplication.class, SampleService.class);
        assertViolation(details, ApplicationToApi.class, ApiToApplication.class);
        assertViolation(details, PresentationToPort.class, SamplePort.class);
    }

    @Test
    @DisplayName("層の依存の規則は、表にない層のクラスを、層の名前を付けた違反にする")
    void layerDependencyRuleMatchesLayerOutsideTable() {
        JavaClasses examples = new ClassFileImporter().importClasses(ExtraLayerClass.class);

        List<String> details = LAYER_DEPENDENCY_RULE.evaluate(examples).getFailureReport().getDetails();

        assertThat(details).singleElement().asString()
                .contains(ExtraLayerClass.class.getName())
                .contains("依存の表にない層 extra");
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
        noClasses().that().resideInAnyPackage(ProductionClasses.MODULE_PACKAGE + ".*.presentation..", ProductionClasses.MODULE_PACKAGE + ".*.application.model..")
                .should().dependOnClassesThat().resideInAPackage(ProductionClasses.MODULE_PACKAGE + ".*.application.port..")
                .check(ProductionClasses.all());
    }

    @Test
    @DisplayName("@Transactional は application と infrastructure だけに付ける")
    void transactionalOnlyInApplicationOrInfrastructure() {
        classes().that().areAnnotatedWith(Transactional.class)
                .should().resideInAnyPackage(ProductionClasses.MODULE_PACKAGE + ".*.application..", ProductionClasses.MODULE_PACKAGE + ".*.infrastructure..")
                .check(ProductionClasses.all());
        methods().that().areAnnotatedWith(Transactional.class)
                .should().beDeclaredInClassesThat()
                .resideInAnyPackage(ProductionClasses.MODULE_PACKAGE + ".*.application..", ProductionClasses.MODULE_PACKAGE + ".*.infrastructure..")
                .check(ProductionClasses.all());
    }

    @Test
    @DisplayName("@RestController は presentation だけに付ける（MeController を除く）")
    void restControllerOnlyInPresentation() {
        classes().that().areAnnotatedWith(RestController.class)
                .and().resideInAPackage("com.ecsite..")
                .and().doNotHaveFullyQualifiedName(ME_CONTROLLER)
                .should().resideInAPackage(ProductionClasses.MODULE_PACKAGE + ".*.presentation..")
                .check(ProductionClasses.all());
    }

    @Test
    @DisplayName("共通コードと設定は、モジュールに依存しない")
    void commonAndConfigDependOnNoModule() {
        noClasses().that().resideInAnyPackage("com.ecsite.common..", "com.ecsite.config..")
                .should().dependOnClassesThat().resideInAPackage(ProductionClasses.MODULE_PACKAGE + "..")
                .check(ProductionClasses.all());
    }

    @Test
    @DisplayName("共通コードは、設定に依存しない")
    void commonDependsOnNoConfig() {
        dependsOnNoPackage("com.ecsite.common..", "com.ecsite.config..").check(ProductionClasses.all());
    }

    @Test
    @DisplayName("共通コードが設定に依存しない規則は、設定に依存するクラスに当たる")
    void commonDependencyRuleMatchesClassDependingOnConfig() {
        JavaClasses examples = new ClassFileImporter().importClasses(CommonToConfig.class, SampleConfig.class);

        List<String> details = dependsOnNoPackage("com.ecsite.architecture.commondeps.sample.common..",
                "com.ecsite.architecture.commondeps.sample.config..")
                .evaluate(examples).getFailureReport().getDetails();

        assertViolation(details, CommonToConfig.class, SampleConfig.class);
    }

    @Test
    @DisplayName("モジュールは、設定に依存しない")
    void modulesDependOnNoConfig() {
        noClasses().that().resideInAPackage(ProductionClasses.MODULE_PACKAGE + "..")
                .should().dependOnClassesThat().resideInAPackage("com.ecsite.config..")
                .check(ProductionClasses.all());
    }

    @Test
    @DisplayName("1 つのパッケージの直下のクラスは 20 を超えない")
    void packagesAreSmallEnough() {
        Map<String, Long> countByPackage = classCountByPackage(ProductionClasses.all());
        ProductionClasses.requireNotEmpty(countByPackage.entrySet(), "手で書いたパッケージ");

        assertThat(countByPackage).allSatisfy((name, count) ->
                assertThat(count).as(name).isLessThanOrEqualTo(MAX_CLASSES_PER_PACKAGE));
    }

    @Test
    @DisplayName("目安を超えるパッケージは、バックエンドの書き方の「パッケージの大きさ」の表と、パッケージも数も一致する")
    void oversizedPackagesMatchTable() throws IOException {
        Map<String, Integer> table = oversizedTable(Files.readAllLines(RULES_BACKEND_CODING, StandardCharsets.UTF_8));
        ProductionClasses.requireNotEmpty(table.entrySet(), "バックエンドの書き方の「パッケージの大きさ」の表の行");

        assertThat(oversizedMismatches(classCountByPackage(ProductionClasses.all()), table)).isEmpty();
    }

    @Test
    @DisplayName("表の外のパッケージが目安を超えると、不一致になる")
    void mismatchWhenPackageOutsideTableExceedsGuideline() {
        Map<String, Long> counted = Map.of("module.order.application", 11L);

        assertThat(oversizedMismatches(counted, Map.of())).containsExactly("表にない超過: order.application（11）");
    }

    @Test
    @DisplayName("表のパッケージが目安以下になると、不一致になる")
    void mismatchWhenPackageInTableIsWithinGuideline() {
        Map<String, Long> counted = Map.of("module.order.application", 10L);

        assertThat(oversizedMismatches(counted, Map.of("order.application", 12)))
                .containsExactly("目安以下: order.application（表 12、実際 10）");
    }

    @Test
    @DisplayName("表のパッケージの数が実際と違うと、不一致になる")
    void mismatchWhenCountDiffers() {
        Map<String, Long> counted = Map.of("config.security", 12L, "module.order.application", 10L);

        assertThat(oversizedMismatches(counted, Map.of("config.security", 11)))
                .containsExactly("数の違い: config.security（表 11、実際 12）");
    }

    @Test
    @DisplayName("表の読み取りは、見出しの後の最初の表の、パッケージと数の行だけを読む")
    void tableIsReadFromFirstTableAfterHeading() {
        List<String> lines = List.of(
                "| 別の表 | 数 |", "|---|---|", "| `x.y` | 99 |",
                "### 1.4 パッケージの大きさ", "本文",
                "| パッケージ | 数 | 分けない理由 |", "|---|---|---|",
                "| `catalog.infrastructure.persistence` | 14 | 理由 |", "| `config.security` | 11 | 理由 |",
                "", "表の後の本文",
                "| 2 つ目の表 | 数 |", "|---|---|", "| `w.w` | 7 |",
                "### 1.5 共通の型", "| `z.z` | 5 | 理由 |");

        assertThat(oversizedTable(lines)).containsExactlyInAnyOrderEntriesOf(
                Map.of("catalog.infrastructure.persistence", 14, "config.security", 11));
    }

    @Test
    @DisplayName("他のモジュールを包む部品の、部品ごとのパッケージ（*dir）がない")
    void noDirectoryPackages() {
        List<String> modulePackages = ProductionClasses.all().stream()
                .map(JavaClass::getPackageName)
                .filter(name -> name.startsWith(ProductionClasses.MODULE_PACKAGE))
                .toList();
        ProductionClasses.requireNotEmpty(modulePackages, "モジュールのクラスのパッケージ");

        Set<String> dirPackages = modulePackages.stream()
                .filter(name -> name.endsWith("dir"))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(dirPackages).isEmpty();
    }

    @Test
    @DisplayName("他のモジュールの api を使うのは infrastructure.directory だけである（許可の一覧を除く）")
    void otherModulesApiIsUsedOnlyByDirectories() {
        Set<String> modulesWithApi = ProductionClasses.modulesWith("api");
        ProductionClasses.requireNotEmpty(modulesWithApi, "api を持つモジュール");

        for (String module : modulesWithApi) {
            classes().that().resideOutsideOfPackages(
                            "%s.%s..".formatted(ProductionClasses.MODULE_PACKAGE, module), ProductionClasses.MODULE_PACKAGE + ".*.infrastructure.directory..")
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

    @Test
    @DisplayName("token の取り出しと会員・運営者の取り出しは、presentation、common.security、許可の一覧のクラスだけが使う")
    void tokenAccessorsAreUsedOnlyInAllowedPlaces() {
        tokenAccessRule(ProductionClasses.MODULE_PACKAGE + ".*.presentation..").check(ProductionClasses.all());
    }

    @Test
    @DisplayName("token の取り出しの規則は、application 層から使う例に当たり、presentation 層から使う例には当たらない")
    void tokenAccessRuleMatchesApplicationExampleOnly() {
        JavaClasses examples = new ClassFileImporter().importClasses(
                ApplicationUsesCurrentMember.class, PresentationUsesCurrentMember.class);

        List<String> details = tokenAccessRule("com.ecsite.architecture.tokenaccess.sample.presentation..")
                .evaluate(examples).getFailureReport().getDetails();

        assertViolation(details, ApplicationUsesCurrentMember.class, CurrentMember.class);
        assertThat(details).noneMatch(detail -> detail.contains(PresentationUsesCurrentMember.class.getName()));
    }

    @Test
    @DisplayName("token の取り出しの規則は、infrastructure.directory から会員 ID の取り出しを使う例に当たる")
    void tokenAccessRuleMatchesDirectoryUsingCurrentMemberIds() {
        JavaClasses examples = new ClassFileImporter().importClasses(DirectoryUsesCurrentMemberIds.class);

        List<String> details = tokenAccessRule("com.ecsite.architecture.tokenaccess.sample.presentation..")
                .evaluate(examples).getFailureReport().getDetails();

        assertViolation(details, DirectoryUsesCurrentMemberIds.class, CurrentMemberIds.class);
    }

    @Test
    @DisplayName("token の取り出しを使う許可の一覧は、全て本番のクラスで使われている")
    void everyTokenAccessExceptionIsUsed() {
        for (String exception : TOKEN_ACCESS_EXCEPTIONS) {
            boolean used = ProductionClasses.all().stream()
                    .filter(javaClass -> outermostName(javaClass).equals(exception))
                    .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                    .anyMatch(dependency -> TOKEN_ACCESSORS.contains(dependency.getTargetClass().getName()));
            assertThat(used).as(exception).isTrue();
        }
    }

    /**
     * token の取り出しを使える場所を、presentation 層、common.security、許可の一覧のクラスだけにする規則を作る。
     *
     * @param presentationPackage presentation 層のパッケージ（例：com.ecsite.module.*.presentation..）
     * @return 規則
     */
    private static ArchRule tokenAccessRule(String presentationPackage) {
        DescribedPredicate<JavaClass> allowedPlace = JavaClass.Predicates.resideInAnyPackage(presentationPackage, "com.ecsite.common.security..")
                .or(DescribedPredicate.describe("listed in the exceptions", javaClass -> TOKEN_ACCESS_EXCEPTIONS.contains(outermostName(javaClass))));
        return noClasses().that(DescribedPredicate.not(allowedPlace))
                .should().dependOnClassesThat(DescribedPredicate.describe(
                        "are token accessors", javaClass -> TOKEN_ACCESSORS.contains(javaClass.getName())));
    }

    /**
     * 入れ子のクラスを含む外側のクラスの名前を返す。
     *
     * @param javaClass クラス
     * @return 入れ子でないクラスの名前
     */
    private static String outermostName(JavaClass javaClass) {
        String name = javaClass.getName();
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
    }

    /**
     * 手で書いたパッケージごとに、入れ子でないクラスの数を返す（package-info は数えない）。
     *
     * @param classes 調べるクラス
     * @return パッケージの名前から、クラスの数への対応（名前の順）
     */
    private static Map<String, Long> classCountByPackage(JavaClasses classes) {
        return classes.stream()
                .filter(javaClass -> !ProductionClasses.isGenerated(javaClass.getPackageName()))
                .filter(JavaClass::isTopLevelClass)
                .filter(javaClass -> !javaClass.getSimpleName().equals("package-info"))
                .collect(Collectors.groupingBy(JavaClass::getPackageName, TreeMap::new, Collectors.counting()));
    }

    /**
     * 目安を超えるパッケージの、数えた結果と表の食い違いを返す。<br />
     * 数えた結果のパッケージの名前は、{@code com.ecsite.} と、モジュールの {@code module.} を除いて表と比べる
     * （表はモジュールのパッケージを {@code <モジュール>.<層>} と書く）。
     *
     * @param countByPackage パッケージの名前（{@code com.ecsite.} を除いた形でも、含む形でもよい）から、クラスの数への対応
     * @param table          表のパッケージの名前から、数への対応
     * @return 食い違いの説明（説明の順）。表の外の超過、表のパッケージの目安以下、数の違い
     */
    private static List<String> oversizedMismatches(Map<String, Long> countByPackage, Map<String, Integer> table) {
        Map<String, Long> counted = countByPackage.entrySet().stream()
                .collect(Collectors.toMap(entry -> tableName(entry.getKey()), Map.Entry::getValue));
        Set<String> names = new TreeSet<>(table.keySet());
        counted.forEach((name, count) -> {
            if (count > GUIDELINE_CLASSES_PER_PACKAGE) {
                names.add(name);
            }
        });

        List<String> mismatches = new ArrayList<>();
        for (String name : names) {
            long actual = counted.getOrDefault(name, 0L);
            Integer listed = table.get(name);
            if (listed == null) {
                mismatches.add("表にない超過: %s（%d）".formatted(name, actual));
            } else if (actual <= GUIDELINE_CLASSES_PER_PACKAGE) {
                mismatches.add("目安以下: %s（表 %d、実際 %d）".formatted(name, listed, actual));
            } else if (actual != listed) {
                mismatches.add("数の違い: %s（表 %d、実際 %d）".formatted(name, listed, actual));
            }
        }
        return mismatches;
    }

    /**
     * パッケージの名前を、表に書く形にする。
     *
     * @param packageName パッケージの名前
     * @return {@code com.ecsite.} と、モジュールの {@code module.} を除いた名前
     */
    private static String tableName(String packageName) {
        String name = packageName.startsWith("com.ecsite.") ? packageName.substring("com.ecsite.".length()) : packageName;
        return name.startsWith("module.") ? name.substring("module.".length()) : name;
    }

    /**
     * バックエンドの書き方の「パッケージの大きさ」の、目安を超えるパッケージの表を読む。<br />
     * 見出しの後の最初の表だけを読む。表の行のうち、1 列目がバッククォートで囲んだパッケージの名前で、2 列目が数の行を読み、
     * 表の後の行（本文や 2 つ目の表）は読まない。
     *
     * @param lines 文書の行
     * @return パッケージの名前から、数への対応
     */
    private static Map<String, Integer> oversizedTable(List<String> lines) {
        Map<String, Integer> table = new TreeMap<>();
        boolean afterHeading = false;
        boolean inTable = false;
        for (String line : lines) {
            if (line.startsWith("#")) {
                if (inTable) {
                    break;
                }
                afterHeading = line.startsWith(PACKAGE_SIZE_HEADING);
                continue;
            }
            if (!afterHeading) {
                continue;
            }
            if (line.startsWith("|")) {
                inTable = true;
                Matcher row = TABLE_ROW.matcher(line);
                if (row.matches()) {
                    table.put(row.group(1), Integer.parseInt(row.group(2)));
                }
            } else if (inTable) {
                break;
            }
        }
        return table;
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
     * あるパッケージが、別のパッケージに依存しない規則を作る。
     *
     * @param sourcePackage 依存する側のパッケージ
     * @param targetPackage 依存されない側のパッケージ
     * @return 規則
     */
    private static ArchRule dependsOnNoPackage(String sourcePackage, String targetPackage) {
        return noClasses().that().resideInAPackage(sourcePackage)
                .should().dependOnClassesThat().resideInAPackage(targetPackage);
    }

    /**
     * 規則の違反の詳細に、指定した依存元から依存先への依存が 1 件あることを確認する。
     *
     * @param details 規則の違反の詳細
     * @param origin  依存元のクラス
     * @param target  依存先のクラス
     */
    private static void assertViolation(List<String> details, Class<?> origin, Class<?> target) {
        assertThat(details)
                .as("%s -> %s", origin.getSimpleName(), target.getSimpleName())
                .filteredOn(detail -> detail.contains(origin.getName()) && detail.contains(target.getName()))
                .hasSize(1);
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
        return new NotUseApiCondition(module);
    }

    /**
     * 層のクラスの、同じモジュールの中への依存が、依存の表にあるという条件。表にない層のクラスは、層の名前を付けて違反にする。
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
     * 他のモジュールの api を、許可の一覧にない使い方で使わないという条件。
     */
    private static final class NotUseApiCondition extends ArchCondition<JavaClass> {

        /**
         * api のパッケージの名前
         */
        private final String apiPackage;

        /**
         * 条件を作る。
         *
         * @param module api を持つモジュールの名前
         */
        NotUseApiCondition(String module) {
            super("%s の api を、許可の一覧にない使い方で使わない".formatted(module));
            this.apiPackage = "%s.%s.api".formatted(ProductionClasses.MODULE_PACKAGE, module);
        }

        /**
         * クラスの依存のうち、許可の一覧にない api への依存を違反にする。
         *
         * @param javaClass 依存元のクラス
         * @param events    違反の記録
         */
        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                if (target.getPackageName().equals(apiPackage) && !isAllowed(javaClass, target)) {
                    events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                }
            }
        }
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
        return "%s.%s.%s..".formatted(ProductionClasses.MODULE_PACKAGE, module, layer);
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
