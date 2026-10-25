package com.ecsite.architecture;

import com.ecsite.ProductionClasses;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * リポジトリと腐敗防止層の名前の形の検査（docs/rules/backend-coding.md 第 5.2 節）。<br />
 * service とイベントの名前は、用途や時制の判断が必要なので、レビューで確認する。
 */
class NamingTest {

    @Test
    @DisplayName("リポジトリの port は domain か application の port に置き、参照用は application に置く")
    void repositoryPortsResideInPortPackages() {
        ProductionClasses.requireNotEmpty(portsNamedWith(ProductionClasses.handwritten(), "Repository"), "名前が Repository で終わる interface");

        assertThat(repositoryPortViolations(ProductionClasses.handwritten())).isEmpty();
    }

    @Test
    @DisplayName("リポジトリの port の実装は、infrastructure.persistence の Jooq と port の名前のクラス")
    void repositoryImplementationsAreNamedJooq() {
        ProductionClasses.requireNotEmpty(implementationsOf(ProductionClasses.handwritten(), "Repository"), "Repository の port の実装のクラス");

        assertThat(implementationViolations(ProductionClasses.handwritten(), "Repository", "Jooq", "", ".infrastructure.persistence"))
                .isEmpty();
    }

    @Test
    @DisplayName("腐敗防止層の窓口は、使う層の port に置く")
    void directoryPortsResideInPortPackages() {
        ProductionClasses.requireNotEmpty(portsNamedWith(ProductionClasses.handwritten(), "Directory"), "名前が Directory で終わる interface");

        assertThat(directoryPortViolations(ProductionClasses.handwritten())).isEmpty();
    }

    @Test
    @DisplayName("腐敗防止層の実装は、infrastructure.directory の <相手>ApiDirectory のクラス")
    void directoryImplementationsAreNamedApiDirectory() {
        ProductionClasses.requireNotEmpty(implementationsOf(ProductionClasses.handwritten(), "Directory"), "Directory の port の実装のクラス");

        assertThat(implementationViolations(ProductionClasses.handwritten(), "Directory", "", "Api", ".infrastructure.directory")).isEmpty();
    }

    @Test
    @DisplayName("名前の規則は、置き場所と名前の合わない例に当たる（合う例は本番のクラスで確認する）")
    void rulesMatchMisplacedAndMisnamedExamples() {
        var examples = new ClassFileImporter().importClasses(
                ExampleRepository.class, ExampleQueryRepository.class, ExampleDirectory.class,
                MisnamedExampleRepositoryImpl.class, MisnamedExampleDirectoryAdapter.class);

        assertThat(repositoryPortViolations(examples))
                .containsExactly(ExampleQueryRepository.class.getName(), ExampleRepository.class.getName());
        assertThat(directoryPortViolations(examples)).containsExactly(ExampleDirectory.class.getName());
        assertThat(implementationViolations(examples, "Repository", "Jooq", "", ".infrastructure.persistence"))
                .containsExactly(MisnamedExampleRepositoryImpl.class.getName());
        assertThat(implementationViolations(examples, "Directory", "", "Api", ".infrastructure.directory"))
                .containsExactly(MisnamedExampleDirectoryAdapter.class.getName());
    }

    @Test
    @DisplayName("腐敗防止層の実装は、public にしない（対象は 1 件以上ある）")
    void directoryImplementationsAreNotPublic() {
        ProductionClasses.requireNotEmpty(
                directoryImplementations(ProductionClasses.handwritten(), ".infrastructure.directory"), "腐敗防止層の実装のクラス");

        assertThat(publicDirectoryImplementations(ProductionClasses.handwritten(), ".infrastructure.directory")).isEmpty();
    }

    @Test
    @DisplayName("public の規則は、public な腐敗防止層の実装の例に当たる")
    void publicRuleMatchesPublicExample() {
        var examples = new ClassFileImporter().importClasses(
                ExampleDirectory.class, PublicExampleApiDirectory.class, MisnamedExampleDirectoryAdapter.class);

        assertThat(publicDirectoryImplementations(examples, ".architecture"))
                .containsExactly(PublicExampleApiDirectory.class.getName());
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
                .filter(javaClass -> javaClass.getRawInterfaces().stream()
                        .anyMatch(port -> port.getPackageName().startsWith("com.ecsite") && port.getSimpleName().endsWith(suffix)))
                .toList();
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
        return classes.stream()
                .filter(JavaClass::isInterface)
                .filter(javaClass -> javaClass.getSimpleName().endsWith("Repository"))
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
        return classes.stream()
                .filter(JavaClass::isInterface)
                .filter(javaClass -> javaClass.getSimpleName().endsWith("Directory"))
                .filter(javaClass -> !javaClass.getPackageName().endsWith(".port"))
                .map(JavaClass::getName)
                .sorted()
                .toList();
    }

    /**
     * 名前か置き場所の合わない、port の実装の名前を返す。
     *
     * @param classes   調べるクラス
     * @param suffix    port の名前の終わり（Repository、Directory）
     * @param prefix    実装の名前の前に付ける語（Jooq）
     * @param infix     port の名前の suffix の前に入れる語（Api）
     * @param packageEnd 実装を置くパッケージの名前の終わり
     * @return 規則に合わない実装のクラスの名前（名前の順）
     */
    private static List<String> implementationViolations(JavaClasses classes, String suffix, String prefix,
                                                         String infix, String packageEnd) {
        var violations = new ArrayList<String>();
        for (var javaClass : classes) {
            if (javaClass.isInterface() || javaClass.isAnonymousClass()) {
                continue;
            }
            for (var port : javaClass.getRawInterfaces()) {
                var portName = port.getSimpleName();
                if (!port.getPackageName().startsWith("com.ecsite") || !portName.endsWith(suffix)) {
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

    /**
     * 規則に当たる例：腐敗防止層の実装が public（public のクラスは、ファイルの名前のクラスしか書けないので、入れ子で置く）
     */
    public static final class PublicExampleApiDirectory implements ExampleDirectory {
    }
}

/**
 * 規則に当たる例：リポジトリの port が port のパッケージにない
 */
interface ExampleRepository {
}

/**
 * 規則に当たる例：参照用のリポジトリの port が application の port のパッケージにない
 */
interface ExampleQueryRepository {
}

/**
 * 規則に当たる例：腐敗防止層の窓口が port にない
 */
interface ExampleDirectory {
}

/**
 * 規則に当たる例：リポジトリの実装の名前が Jooq と port の名前でない
 */
final class MisnamedExampleRepositoryImpl implements ExampleRepository {
}

/**
 * 規則に当たる例：腐敗防止層の実装の名前が ApiDirectory の形でない
 */
final class MisnamedExampleDirectoryAdapter implements ExampleDirectory {
}
