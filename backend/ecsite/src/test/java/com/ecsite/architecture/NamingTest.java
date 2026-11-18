package com.ecsite.architecture;

import com.ecsite.architecture.sample.naming.ExampleDirectory;
import com.ecsite.architecture.sample.naming.ExampleNonTransactionalCommandService;
import com.ecsite.architecture.sample.naming.ExampleOrderService;
import com.ecsite.architecture.sample.naming.ExampleQueryRepository;
import com.ecsite.architecture.sample.naming.ExampleRepository;
import com.ecsite.architecture.sample.naming.ExampleTransactionalGatewayService;
import com.ecsite.architecture.sample.naming.ExampleWritableQueryService;
import com.ecsite.architecture.sample.naming.MisnamedExampleRepositoryImpl;
import com.ecsite.architecture.sample.naming.NamingExamples;
import com.ecsite.architecture.sample.naming.PublicExampleApiDirectory;
import com.ecsite.support.ProductionClasses;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * リポジトリと腐敗防止層と service の名前の形の検査
 *
 * <p>根拠はバックエンドの書き方の「名前の形」。service のトランザクションの付け方の根拠は、同じ文書の「トランザクションは application」。
 * イベントの名前は、時制の判断が必要なので、レビューで確認する。
 *
 * <p>service の検査の対象は、{@code com.ecsite.module.*.application..} にある、{@code @Service} を付けた手書きのクラス。
 */
class NamingTest {

    // ============================== 定数 ==============================

    /**
     * service の名前の検査の例外
     *
     * <p>完全修飾名から、例外にする理由への対応。
     */
    private static final Map<String, String> NAME_EXCEPTIONS = Map.of(
            "com.ecsite.module.cart.application.CartService", "読み書きが混ざるレベル B の service で、参照と更新のどちらの名前にも当てはまらない",
            "com.ecsite.module.media.application.MediaService", "読み書きが混ざるレベル B の service で、参照と更新のどちらの名前にも当てはまらない",
            "com.ecsite.module.shipping.application.ShipmentService", "読み書きが混ざるレベル B の service で、参照と更新のどちらの名前にも当てはまらない");

    /**
     * service のトランザクションの検査の例外
     *
     * <p>完全修飾名から、例外にする理由への対応。
     */
    private static final Map<String, String> TRANSACTION_EXCEPTIONS = Map.of(
            "com.ecsite.module.reporting.application.ReportSettingsQueryService",
            "DB を使わない参照の service で、設定の値だけを返すので、トランザクションを付けると不要な DB の接続を取る");

    // ============================== テスト ==============================

    @Nested
    @DisplayName("リポジトリ")
    class Repositories {

        @Test
        @DisplayName("リポジトリの port は domain か application の port に置き、参照用は application に置く")
        void repositoryPortsResideInPortPackages() {
            // 準備：対象の port が 1 件以上ある
            ProductionClasses.requireNotEmpty(portsNamedWith(ProductionClasses.handwritten(), "Repository"), "名前が Repository で終わる interface");

            // 操作と確認：本番のクラスに、置き場所の合わない port がない
            assertThat(repositoryPortViolations(ProductionClasses.handwritten())).isEmpty();
        }

        @Test
        @DisplayName("リポジトリの port の実装は、infrastructure.persistence の Jooq と port の名前のクラス")
        void repositoryImplementationsAreNamedJooq() {
            // 準備：対象の実装が 1 件以上ある
            ProductionClasses.requireNotEmpty(implementationsOf(ProductionClasses.handwritten(), "Repository"), "Repository の port の実装のクラス");

            // 操作と確認：本番のクラスに、名前か置き場所の合わない実装がない
            assertThat(implementationViolations(ProductionClasses.handwritten(), "Repository", "Jooq", "", ".infrastructure.persistence"))
                    .isEmpty();
        }

        @Test
        @DisplayName("port の置き場所の規則は、port のパッケージにない port に当たる")
        void portRuleMatchesMisplacedExamples() {
            // 操作と確認：置き場所の合わない例（更新用と参照用）が違反になる（合う例は本番のクラスで確認する）
            assertThat(repositoryPortViolations(namingExamples()))
                    .containsExactly(ExampleQueryRepository.class.getName(), ExampleRepository.class.getName());
        }

        @Test
        @DisplayName("実装の名前の規則は、Jooq と port の名前でない実装に当たる")
        void implementationRuleMatchesMisnamedExample() {
            // 操作と確認：名前の合わない例が違反になる
            assertThat(implementationViolations(namingExamples(), "Repository", "Jooq", "", ".infrastructure.persistence"))
                    .containsExactly(MisnamedExampleRepositoryImpl.class.getName());
        }
    }

    @Nested
    @DisplayName("腐敗防止層")
    class Directories {

        @Test
        @DisplayName("腐敗防止層の窓口は、使う層の port に置く")
        void directoryPortsResideInPortPackages() {
            // 準備：対象の窓口が 1 件以上ある
            ProductionClasses.requireNotEmpty(portsNamedWith(ProductionClasses.handwritten(), "Directory"), "名前が Directory で終わる interface");

            // 操作と確認：本番のクラスに、置き場所の合わない窓口がない
            assertThat(directoryPortViolations(ProductionClasses.handwritten())).isEmpty();
        }

        @Test
        @DisplayName("腐敗防止層の実装は、infrastructure.directory の <相手>ApiDirectory のクラス")
        void directoryImplementationsAreNamedApiDirectory() {
            // 準備：対象の実装が 1 件以上ある
            ProductionClasses.requireNotEmpty(implementationsOf(ProductionClasses.handwritten(), "Directory"), "Directory の port の実装のクラス");

            // 操作と確認：本番のクラスに、名前か置き場所の合わない実装がない
            assertThat(implementationViolations(ProductionClasses.handwritten(), "Directory", "", "Api", ".infrastructure.directory")).isEmpty();
        }

        @Test
        @DisplayName("腐敗防止層の実装は、public にしない")
        void directoryImplementationsAreNotPublic() {
            // 準備：対象の実装が 1 件以上ある
            ProductionClasses.requireNotEmpty(
                    directoryImplementations(ProductionClasses.handwritten(), ".infrastructure.directory"), "腐敗防止層の実装のクラス");

            // 操作と確認：本番のクラスに、public な実装がない
            assertThat(publicDirectoryImplementations(ProductionClasses.handwritten(), ".infrastructure.directory")).isEmpty();
        }

        @Test
        @DisplayName("窓口の置き場所の規則は、port のパッケージにない窓口に当たる")
        void portRuleMatchesMisplacedExample() {
            // 操作と確認：置き場所の合わない例が違反になる
            assertThat(directoryPortViolations(namingExamples())).containsExactly(ExampleDirectory.class.getName());
        }

        @Test
        @DisplayName("実装の名前の規則は、<相手>ApiDirectory の形でない実装に当たる")
        void implementationRuleMatchesMisnamedExample() {
            // 操作と確認：名前の合わない例が違反になる
            assertThat(implementationViolations(namingExamples(), "Directory", "", "Api", ".infrastructure.directory"))
                    .containsExactly(NamingExamples.MISNAMED_DIRECTORY_ADAPTER.getName());
        }

        @Test
        @DisplayName("public の規則は、public な腐敗防止層の実装の例に当たる")
        void publicRuleMatchesPublicExample() {
            // 準備：窓口と、public な実装と、public でない実装
            var examples = new ClassFileImporter().importClasses(
                    ExampleDirectory.class, PublicExampleApiDirectory.class, NamingExamples.MISNAMED_DIRECTORY_ADAPTER);

            // 操作と確認：public な実装だけが違反になる
            assertThat(publicDirectoryImplementations(examples, ".naming"))
                    .containsExactly(PublicExampleApiDirectory.class.getName());
        }
    }

    @Nested
    @DisplayName("service")
    class Services {

        @Test
        @DisplayName("service の名前は、QueryService・CommandService・GatewayService のどれかで終わる")
        void serviceNamesEndWithRoleSuffix() {
            // 準備：対象の service が 1 件以上ある
            ProductionClasses.requireNotEmpty(productionServices(), "application の @Service のクラス");

            // 操作と確認：本番のクラスに、名前の合わない service がない
            assertThat(serviceNameViolations(productionServices())).isEmpty();
        }

        @Test
        @DisplayName("参照と更新の service は、クラスに @Transactional を付ける")
        void queryAndCommandServicesAreTransactional() {
            // 準備：対象の service が 1 件以上ある
            ProductionClasses.requireNotEmpty(
                    productionServices().stream().filter(NamingTest::endsWithQueryOrCommand).toList(),
                    "名前が QueryService か CommandService で終わる service");

            // 操作と確認：本番のクラスに、@Transactional のない参照と更新の service がない
            assertThat(missingTransactionViolations(productionServices())).isEmpty();
        }

        @Test
        @DisplayName("参照の service は、クラスの @Transactional を読み取り専用にする")
        void queryServicesAreReadOnly() {
            // 準備：対象の service が 1 件以上ある
            ProductionClasses.requireNotEmpty(
                    productionServices().stream().filter(javaClass -> javaClass.getSimpleName().endsWith("QueryService")).toList(),
                    "名前が QueryService で終わる service");

            // 操作と確認：本番のクラスに、読み取り専用でない参照の service がない
            assertThat(writableQueryViolations(productionServices())).isEmpty();
        }

        @Test
        @DisplayName("外部の呼び出しの service は、クラスに @Transactional を付けない")
        void gatewayServicesAreNotTransactional() {
            // 準備：対象の service が 1 件以上ある
            ProductionClasses.requireNotEmpty(
                    productionServices().stream().filter(javaClass -> javaClass.getSimpleName().endsWith("GatewayService")).toList(),
                    "名前が GatewayService で終わる service");

            // 操作と確認：本番のクラスに、@Transactional を付けた外部の呼び出しの service がない
            assertThat(gatewayTransactionViolations(productionServices())).isEmpty();
        }

        @Test
        @DisplayName("名前の規則は、3 つの語のどれでも終わらない service に当たる")
        void nameRuleMatchesMisnamedExample() {
            // 操作と確認：名前の合わない例だけが違反になる
            assertThat(serviceNameViolations(exampleServices())).containsExactly(ExampleOrderService.class.getName());
        }

        @Test
        @DisplayName("トランザクションの規則は、@Transactional のない更新の service に当たる")
        void transactionRuleMatchesNonTransactionalCommandExample() {
            // 操作と確認：@Transactional のない更新の例だけが違反になる
            assertThat(missingTransactionViolations(exampleServices()))
                    .containsExactly(ExampleNonTransactionalCommandService.class.getName());
        }

        @Test
        @DisplayName("読み取り専用の規則は、読み取り専用でない参照の service に当たる")
        void readOnlyRuleMatchesWritableQueryExample() {
            // 操作と確認：読み取り専用でない参照の例だけが違反になる
            assertThat(writableQueryViolations(exampleServices()))
                    .containsExactly(ExampleWritableQueryService.class.getName());
        }

        @Test
        @DisplayName("外部の呼び出しの規則は、@Transactional を付けた外部の呼び出しの service に当たる")
        void gatewayRuleMatchesTransactionalExample() {
            // 操作と確認：@Transactional を付けた外部の呼び出しの例だけが違反になる
            assertThat(gatewayTransactionViolations(exampleServices()))
                    .containsExactly(ExampleTransactionalGatewayService.class.getName());
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 検査の対象の、本番の service を返す。
     *
     * @return application にある、{@code @Service} を付けた手書きのクラス
     */
    private static List<JavaClass> productionServices() {
        return ProductionClasses.handwritten().stream()
                .filter(javaClass -> JavaClass.Predicates.resideInAPackage("com.ecsite.module.*.application..").test(javaClass))
                .filter(javaClass -> javaClass.isTopLevelClass() && javaClass.isAnnotatedWith(Service.class))
                .toList();
    }

    /**
     * 名前かトランザクションの合わない service の例を返す。
     *
     * @return 例の service のクラス
     */
    private static List<JavaClass> exampleServices() {
        return new ClassFileImporter().importClasses(
                        ExampleOrderService.class, ExampleNonTransactionalCommandService.class, ExampleTransactionalGatewayService.class,
                        ExampleWritableQueryService.class)
                .stream().toList();
    }

    /**
     * 置き場所と名前の合わない例を読む。
     *
     * @return リポジトリと腐敗防止層の、置き場所と名前の合わない port と実装の例
     */
    private static JavaClasses namingExamples() {
        return new ClassFileImporter().importClasses(
                ExampleRepository.class, ExampleQueryRepository.class, ExampleDirectory.class,
                MisnamedExampleRepositoryImpl.class, NamingExamples.MISNAMED_DIRECTORY_ADAPTER);
    }

    // ============================== 規則の補助 ==============================

    /**
     * 名前が QueryService か CommandService で終わるかを返す。
     *
     * @param javaClass 調べる service
     * @return 参照か更新の名前なら true
     */
    private static boolean endsWithQueryOrCommand(JavaClass javaClass) {
        var name = javaClass.getSimpleName();
        return name.endsWith("QueryService") || name.endsWith("CommandService");
    }

    /**
     * 名前が QueryService・CommandService・GatewayService のどれでも終わらない service の名前を返す。
     *
     * @param services 調べる service
     * @return 規則に合わない service の名前（名前の順。例外の表の service を除く）
     */
    private static List<String> serviceNameViolations(List<JavaClass> services) {
        return services.stream()
                .filter(javaClass -> !endsWithQueryOrCommand(javaClass) && !javaClass.getSimpleName().endsWith("GatewayService"))
                .map(JavaClass::getName)
                .filter(name -> !NAME_EXCEPTIONS.containsKey(name))
                .sorted()
                .toList();
    }

    /**
     * クラスに {@code @Transactional} を付けていない、参照と更新の service の名前を返す。
     *
     * @param services 調べる service
     * @return 規則に合わない service の名前（名前の順。例外の表の service を除く）
     */
    private static List<String> missingTransactionViolations(List<JavaClass> services) {
        return services.stream()
                .filter(NamingTest::endsWithQueryOrCommand)
                .filter(javaClass -> !javaClass.isAnnotatedWith(Transactional.class))
                .map(JavaClass::getName)
                .filter(name -> !TRANSACTION_EXCEPTIONS.containsKey(name))
                .sorted()
                .toList();
    }

    /**
     * クラスの {@code @Transactional} が読み取り専用でない、参照の service の名前を返す。
     *
     * <p>{@code @Transactional} がない service は、{@link #missingTransactionViolations} が見つけるので、ここでは除く。
     *
     * @param services 調べる service
     * @return 規則に合わない service の名前（名前の順。例外の表の service を除く）
     */
    private static List<String> writableQueryViolations(List<JavaClass> services) {
        return services.stream()
                .filter(javaClass -> javaClass.getSimpleName().endsWith("QueryService"))
                .filter(javaClass -> javaClass.isAnnotatedWith(Transactional.class))
                .filter(javaClass -> !javaClass.getAnnotationOfType(Transactional.class).readOnly())
                .map(JavaClass::getName)
                .filter(name -> !TRANSACTION_EXCEPTIONS.containsKey(name))
                .sorted()
                .toList();
    }

    /**
     * クラスに {@code @Transactional} を付けている、外部の呼び出しの service の名前を返す。
     *
     * @param services 調べる service
     * @return 規則に合わない service の名前（名前の順）
     */
    private static List<String> gatewayTransactionViolations(List<JavaClass> services) {
        return services.stream()
                .filter(javaClass -> javaClass.getSimpleName().endsWith("GatewayService"))
                .filter(javaClass -> javaClass.isAnnotatedWith(Transactional.class))
                .map(JavaClass::getName)
                .sorted()
                .toList();
    }

    /**
     * 名前が指定した語で終わる interface（port）を返す。
     *
     * @param classes 調べるクラス
     * @param suffix  port の名前の終わり（Repository、Directory）
     * @return port の interface
     */
    private static List<JavaClass> portsNamedWith(JavaClasses classes, String suffix) {
        return classes.stream()
                .filter(JavaClass::isInterface)
                .filter(javaClass -> javaClass.getSimpleName().endsWith(suffix))
                .toList();
    }

    /**
     * 名前が指定した語で終わる port を、実装するクラスを返す。
     *
     * @param classes 調べるクラス
     * @param suffix  port の名前の終わり（Repository、Directory）
     * @return port の実装のクラス
     */
    private static List<JavaClass> implementationsOf(JavaClasses classes, String suffix) {
        return classes.stream()
                .filter(javaClass -> !javaClass.isInterface() && !javaClass.isAnonymousClass())
                .filter(javaClass -> javaClass.getRawInterfaces().stream().anyMatch(port -> isPortNamedWith(port, suffix)))
                .toList();
    }

    /**
     * interface が、このプロジェクトの、名前が指定した語で終わる port かを返す。
     *
     * @param port   interface
     * @param suffix port の名前の終わり（Repository、Directory）
     * @return このプロジェクトの interface で、名前が suffix で終わるなら true
     */
    private static boolean isPortNamedWith(JavaClass port, String suffix) {
        return port.getPackageName().startsWith("com.ecsite") && port.getSimpleName().endsWith(suffix);
    }

    /**
     * public な腐敗防止層の実装の名前を返す。
     *
     * @param classes    調べるクラス
     * @param packageEnd 実装を置くパッケージの名前の終わり
     * @return public な実装のクラスの名前（名前の順）
     */
    private static List<String> publicDirectoryImplementations(JavaClasses classes, String packageEnd) {
        return directoryImplementations(classes, packageEnd).stream()
                .filter(javaClass -> javaClass.getModifiers().contains(JavaModifier.PUBLIC))
                .map(JavaClass::getName)
                .sorted()
                .toList();
    }

    /**
     * 腐敗防止層の窓口（名前が Directory で終わる interface）を実装する、クラスを返す。
     *
     * @param classes    調べるクラス
     * @param packageEnd 実装を置くパッケージの名前の終わり
     * @return 実装のクラス
     */
    private static List<JavaClass> directoryImplementations(JavaClasses classes, String packageEnd) {
        return classes.stream()
                .filter(javaClass -> !javaClass.isAnonymousClass())
                .filter(javaClass -> !javaClass.isInterface())
                .filter(javaClass -> javaClass.getPackageName().endsWith(packageEnd))
                .filter(javaClass -> javaClass.getRawInterfaces().stream()
                        .anyMatch(port -> port.getSimpleName().endsWith("Directory")))
                .toList();
    }

    /**
     * 置き場所の合わないリポジトリの port の名前を返す。
     *
     * @param classes 調べるクラス
     * @return port のパッケージにない、または参照用なのに application にない interface の名前（名前の順）
     */
    private static List<String> repositoryPortViolations(JavaClasses classes) {
        return portsNamedWith(classes, "Repository").stream()
                .filter(javaClass -> {
                    var packageName = javaClass.getPackageName();
                    var inPort = packageName.endsWith(".port");
                    var inApplication = packageName.contains(".application.");
                    var inDomain = packageName.contains(".domain.");
                    var query = javaClass.getSimpleName().endsWith("QueryRepository");
                    return !inPort || !(inApplication || inDomain) || (query && !inApplication);
                })
                .map(JavaClass::getName)
                .sorted()
                .toList();
    }

    /**
     * 置き場所の合わない腐敗防止層の窓口の名前を返す。
     *
     * @param classes 調べるクラス
     * @return port のパッケージにない、名前が Directory で終わる interface の名前（名前の順）
     */
    private static List<String> directoryPortViolations(JavaClasses classes) {
        return portsNamedWith(classes, "Directory").stream()
                .filter(javaClass -> !javaClass.getPackageName().endsWith(".port"))
                .map(JavaClass::getName)
                .sorted()
                .toList();
    }

    /**
     * 名前か置き場所の合わない、port の実装の名前を返す。
     *
     * @param classes    調べるクラス
     * @param suffix     port の名前の終わり（Repository、Directory）
     * @param prefix     実装の名前の前に付ける語（Jooq）
     * @param infix      port の名前の suffix の前に入れる語（Api）
     * @param packageEnd 実装を置くパッケージの名前の終わり
     * @return 規則に合わない実装のクラスの名前（名前の順）
     */
    private static List<String> implementationViolations(JavaClasses classes, String suffix, String prefix,
                                                         String infix, String packageEnd) {
        var violations = new ArrayList<String>();
        for (var javaClass : implementationsOf(classes, suffix)) {
            for (var port : javaClass.getRawInterfaces()) {
                var portName = port.getSimpleName();
                if (!isPortNamedWith(port, suffix)) {
                    continue;
                }
                var stem = portName.substring(0, portName.length() - suffix.length());
                var expected = prefix + stem + infix + suffix;
                if (!javaClass.getSimpleName().equals(expected) || !javaClass.getPackageName().endsWith(packageEnd)) {
                    violations.add(javaClass.getName());
                }
            }
        }
        return violations.stream().sorted().toList();
    }
}
