package com.ecsite.architecture;

import com.ecsite.ProductionClasses;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Locale;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 設定の値の型と設定のクラスの検査（docs/rules/backend-coding.md 第 6.1 節）。
 */
class ConfigurationPropertiesTest {

    /**
     * 秘密の値の項目の名前に含まれる語（小文字）。名前が key で終わる項目も秘密の値とみなす。
     */
    private static final List<String> SECRET_WORDS = List.of("secret", "password", "salt", "token", "credential");

    /**
     * 秘密の値の項目を持つ設定の値の型（{@code @ConfigurationProperties} を付けたクラス）
     */
    private static final DescribedPredicate<JavaClass> SECRET_PROPERTIES = DescribedPredicate.describe(
            "configuration properties with a secret field",
            javaClass -> javaClass.isAnnotatedWith(ConfigurationProperties.class)
                    && javaClass.getFields().stream().anyMatch(field -> isSecretFieldName(field.getName())));

    @Test
    @DisplayName("@EnableConfigurationProperties を付けるクラスは、設定のクラス（@Configuration）")
    void enableConfigurationPropertiesIsOnConfiguration() {
        ArchRule rule = classes().that().areAnnotatedWith(EnableConfigurationProperties.class)
                .should().beMetaAnnotatedWith(Configuration.class);
        rule.check(ProductionClasses.handwritten());
    }

    @Test
    @DisplayName("モジュールの設定のクラス（@Configuration）は、infrastructure に置く")
    void moduleConfigurationResidesInInfrastructure() {
        moduleConfigurationRule(ProductionClasses.MODULE_PACKAGE + "..").check(ProductionClasses.handwritten());
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
        ProductionClasses.requireNotEmpty(
                ProductionClasses.handwritten().stream()
                        .filter(javaClass -> javaClass.getPackageName().contains(".presentation")
                                && javaClass.getPackageName().startsWith(ProductionClasses.MODULE_PACKAGE_PREFIX))
                        .toList(),
                "モジュールの presentation のクラス");

        presentationRule(ProductionClasses.MODULE_PACKAGE + ".*.presentation..").check(ProductionClasses.handwritten());
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

    @Test
    @DisplayName("秘密の値の項目を持つ設定の値の型は、toString を宣言する")
    void secretPropertiesDeclareToString() {
        ProductionClasses.requireNotEmpty(
                ProductionClasses.handwritten().stream()
                        .filter(javaClass -> SECRET_PROPERTIES.test(javaClass))
                        .toList(),
                "秘密の値の項目を持つ設定の値の型");

        secretPropertiesRule().check(ProductionClasses.handwritten());
    }

    @Test
    @DisplayName("toString の規則は、秘密の値の項目を持ち toString を宣言しない例に当たる")
    void toStringRuleMatchesExampleWithoutToString() {
        var examples = new ClassFileImporter().importClasses(SecretWithoutToStringProperties.class);

        var result = secretPropertiesRule().evaluate(examples);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains(SecretWithoutToStringProperties.class.getName()));
    }

    @Test
    @DisplayName("toString の規則は、toString を宣言する例と、秘密の値の項目を持たない例に当たらない")
    void toStringRuleDoesNotMatchCompliantExamples() {
        var examples = new ClassFileImporter().importClasses(SecretWithToStringProperties.class, ExampleProperties.class);

        assertThat(secretPropertiesRule().evaluate(examples).hasViolation()).isFalse();
    }

    @Test
    @DisplayName("秘密の値の項目の名前は、secret、password、salt、token、credential を含むか、key で終わる")
    void detectsSecretFieldNames() {
        assertThat(List.of("secretAccessKey", "webhookSecret", "salt", "key", "secretKey", "apiPassword", "accessToken", "credentials"))
                .allMatch(ConfigurationPropertiesTest::isSecretFieldName);
        assertThat(List.of("accessKeyId", "value", "endpoint", "keyId", "monkeyBusiness"))
                .noneMatch(ConfigurationPropertiesTest::isSecretFieldName);
    }

    /**
     * 秘密の値の項目を持つ設定の値の型が toString を宣言する規則を作る。
     *
     * @return 規則
     */
    private static ArchRule secretPropertiesRule() {
        return classes().that(SECRET_PROPERTIES)
                .should(new ArchCondition<JavaClass>("toString を宣言する") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        // record のコンパイラが作る toString は final で、宣言した toString と区別できないので、final を除く
                        boolean declared = javaClass.getMethods().stream()
                                .anyMatch(method -> method.getName().equals("toString")
                                        && method.getRawParameterTypes().isEmpty()
                                        && !method.getModifiers().contains(JavaModifier.FINAL));
                        events.add(new SimpleConditionEvent(javaClass, declared,
                                "%s は秘密の値の項目を持つが、toString を宣言していない".formatted(javaClass.getName())));
                    }
                });
    }

    /**
     * 秘密の値の項目の名前かを返す。名前を小文字にして判定する。
     *
     * @param name 項目の名前
     * @return 秘密の値の項目なら true
     */
    private static boolean isSecretFieldName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return SECRET_WORDS.stream().anyMatch(lower::contains) || lower.endsWith("key");
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

/**
 * 規則に当たる例：秘密の値の項目を持つが、toString を宣言しない
 *
 * @param apiKey 例の秘密の値
 */
@ConfigurationProperties("example-secret")
record SecretWithoutToStringProperties(String apiKey) {
}

/**
 * 規則に当たらない例：秘密の値の項目を持ち、toString を宣言する
 *
 * @param apiKey 例の秘密の値
 */
@ConfigurationProperties("example-secret-safe")
record SecretWithToStringProperties(String apiKey) {

    @Override
    public String toString() {
        return "SecretWithToStringProperties[apiKey=***]";
    }
}
