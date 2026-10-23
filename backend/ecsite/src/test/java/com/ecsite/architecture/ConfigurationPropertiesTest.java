package com.ecsite.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 設定の値の型と設定のクラスの検査（docs/rules/backend-coding.md 第 6.1 節）。
 */
class ConfigurationPropertiesTest {

    /**
     * 本番のクラス（テストと、生成したコードを除く）
     */
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(location -> !location.contains("/com/ecsite/jooq/") && !location.contains("/com/ecsite/api/"))
            .importPackages("com.ecsite");

    @Test
    @DisplayName("@EnableConfigurationProperties を付けるクラスは、設定のクラス（@Configuration）")
    void enableConfigurationPropertiesIsOnConfiguration() {
        ArchRule rule = classes().that().areAnnotatedWith(EnableConfigurationProperties.class)
                .should().beMetaAnnotatedWith(Configuration.class);
        rule.check(CLASSES);
    }

    @Test
    @DisplayName("モジュールの設定のクラス（@Configuration）は、infrastructure に置く")
    void moduleConfigurationResidesInInfrastructure() {
        moduleConfigurationRule("com.ecsite.module..").check(CLASSES);
    }

    @Test
    @DisplayName("設定のクラスの規則は、infrastructure の外に置いた例に当たる")
    void configurationRuleMatchesMisplacedExample() {
        var examples = new ClassFileImporter().importClasses(MisplacedExampleConfig.class);

        var result = moduleConfigurationRule("com.ecsite.architecture..").evaluate(examples);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains(MisplacedExampleConfig.class.getName()));
    }

    @Test
    @DisplayName("presentation は、設定の値の型（@ConfigurationProperties）に依存しない")
    void presentationDoesNotDependOnConfigurationProperties() {
        // 規則が空振りしないように、presentation のクラスがあることを確かめる
        assertThat(CLASSES.stream().filter(javaClass -> javaClass.getPackageName().contains(".presentation")
                && javaClass.getPackageName().startsWith("com.ecsite.module."))).isNotEmpty();

        presentationRule("com.ecsite.module.*.presentation..").check(CLASSES);
    }

    @Test
    @DisplayName("presentation の規則は、設定の値の型を使う例に当たる")
    void presentationRuleMatchesExampleUsingProperties() {
        var examples = new ClassFileImporter().importClasses(ExampleProperties.class, PresentationExampleController.class);

        var result = presentationRule("com.ecsite.architecture..").evaluate(examples);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains(PresentationExampleController.class.getName()));
    }

    /**
     * presentation が設定の値の型に依存しない規則を作る。
     *
     * @param presentationPackage 対象にするパッケージ（例：com.ecsite.module.*.presentation..）
     * @return 規則
     */
    private static ArchRule presentationRule(String presentationPackage) {
        return noClasses().that().resideInAPackage(presentationPackage)
                .should().dependOnClassesThat().areAnnotatedWith(ConfigurationProperties.class);
    }

    /**
     * モジュールの設定のクラスを infrastructure に置く規則を作る。
     *
     * @param modulePackage 対象にするパッケージ（例：com.ecsite.module..）
     * @return 規則
     */
    private static ArchRule moduleConfigurationRule(String modulePackage) {
        return classes().that().resideInAPackage(modulePackage)
                .and().areMetaAnnotatedWith(Configuration.class)
                .should().resideInAPackage("..infrastructure..");
    }
}

/**
 * 規則に当たる例：設定のクラスが infrastructure にない
 */
@Configuration(proxyBeanMethods = false)
class MisplacedExampleConfig {
}

/**
 * 例の設定の値の型
 *
 * @param value 例の値
 */
@ConfigurationProperties("example")
record ExampleProperties(String value) {
}

/**
 * 規則に当たる例：コントローラーが設定の値の型を受け取る
 */
class PresentationExampleController {

    /**
     * 例の設定
     */
    private final ExampleProperties properties;

    PresentationExampleController(ExampleProperties properties) {
        this.properties = properties;
    }

    String value() {
        return properties.value();
    }
}
