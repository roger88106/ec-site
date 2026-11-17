package com.ecsite.architecture;

import com.ecsite.architecture.sample.naming.ExampleDirectory;
import com.ecsite.architecture.sample.naming.ExampleQueryRepository;
import com.ecsite.architecture.sample.naming.ExampleRepository;
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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * リポジトリと腐敗防止層の名前の形の検査
 *
 * <p>根拠はバックエンドの書き方の「名前の形」。
 * service とイベントの名前は、用途や時制の判断が必要なので、レビューで確認する。
 */
class NamingTest {

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

    // ============================== 準備の補助 ==============================

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
