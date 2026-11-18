package com.ecsite.architecture;

import com.ecsite.EcSiteApplication;
import com.ecsite.architecture.sample.configprops.EnumFieldProperties;
import com.ecsite.architecture.sample.configprops.ExampleProperties;
import com.ecsite.architecture.sample.configprops.MappedSecretProperties;
import com.ecsite.architecture.sample.configprops.MethodAnnotatedExampleConfig;
import com.ecsite.architecture.sample.configprops.MisplacedExampleConfig;
import com.ecsite.architecture.sample.configprops.NestedSecretProperties;
import com.ecsite.architecture.sample.configprops.NestedSecretSafeProperties;
import com.ecsite.architecture.sample.configprops.PresentationExampleController;
import com.ecsite.architecture.sample.configprops.SecretClassWithFinalToStringProperties;
import com.ecsite.architecture.sample.configprops.SecretWithFinalToStringProperties;
import com.ecsite.architecture.sample.configprops.SecretWithParameterizedToStringProperties;
import com.ecsite.architecture.sample.configprops.SecretWithToStringProperties;
import com.ecsite.architecture.sample.configprops.SecretWithoutToStringProperties;
import com.ecsite.architecture.sample.configprops.StaticConstantProperties;
import com.ecsite.support.ProductionClasses;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeModel;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 設定の値の型と設定のクラスの検査
 *
 * <p>根拠はバックエンドの書き方の「設定の値の型」。
 */
class ConfigurationPropertiesTest {

    // ============================== 定数 ==============================

    /**
     * 秘密の値の項目の名前に含まれる語
     *
     * <p>小文字で持ち、名前の小文字に含まれれば秘密の値とみなす。credential と key は、語に分けて別に判定する。
     */
    private static final List<String> SECRET_WORDS = List.of("secret", "password", "salt", "token");

    /**
     * 名前の最後の語が key のとき、直前にあれば秘密の値とみなす語
     */
    private static final Set<String> KEY_PREFIX_WORDS =
            Set.of("access", "api", "encryption", "hmac", "master", "private", "secret", "signing");

    /**
     * 名前の最後にあると、credential を含んでも秘密の値としない語
     */
    private static final Set<String> CREDENTIAL_LOCATION_WORDS = Set.of("file", "path", "dir");

    /**
     * 設定の型の項目からたどる型のパッケージ
     *
     * <p>自分のコードの型だけをたどる。
     */
    private static final String OWN_PACKAGE_PREFIX = "com.ecsite.";

    // ============================== テスト ==============================

    @Test
    @DisplayName("本番のクラスは、@EnableConfigurationProperties を使わない")
    void productionDoesNotUseEnableConfigurationProperties() {
        // 操作と確認：本番の手書きのクラスに規則を当てる
        // アプリのクラスは、@ConfigurationPropertiesScan が @EnableConfigurationProperties を含むので、検査の対象から除く
        ArchRule rule = noClasses().that().doNotHaveFullyQualifiedName(EcSiteApplication.class.getName())
                .should().beMetaAnnotatedWith(EnableConfigurationProperties.class)
                .because("設定の型の登録は、アプリのクラスの @ConfigurationPropertiesScan にまとめるため");
        rule.check(ProductionClasses.handwritten());
    }

    @Test
    @DisplayName("本番のメソッドには、@ConfigurationProperties を付けない")
    void productionMethodsAreNotAnnotatedWithConfigurationProperties() {
        // 操作と確認：本番の手書きのクラスに規則を当てる
        methodConfigurationPropertiesRule().check(ProductionClasses.handwritten());
    }

    @Test
    @DisplayName("メソッドの規則は、@ConfigurationProperties を付けたメソッドの例に当たる")
    void methodRuleMatchesAnnotatedMethodExample() {
        // 準備：@Bean のメソッドに @ConfigurationProperties を付けた例
        var examples = new ClassFileImporter().importClasses(MethodAnnotatedExampleConfig.class);

        // 操作：規則を当てる
        var result = methodConfigurationPropertiesRule().evaluate(examples);

        // 確認：例のメソッドが違反になる
        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains(MethodAnnotatedExampleConfig.class.getName()));
    }

    @Test
    @DisplayName("本番の設定の型（@ConfigurationProperties）は、com.ecsite.config か com.ecsite.module の下にある")
    void configurationPropertiesResideInScanPackages() {
        // 操作と確認：本番の手書きのクラスに規則を当てる
        configurationPropertiesLocationRule().check(ProductionClasses.handwritten());
    }

    @Test
    @DisplayName("設定の型の置き場所の規則は、範囲の外に置いた例に当たる")
    void configurationPropertiesLocationRuleMatchesOutOfScopeExample() {
        // 準備：スキャンの範囲の外の設定の型の例
        var examples = new ClassFileImporter().importClasses(ExampleProperties.class);

        // 操作：規則を当てる
        var result = configurationPropertiesLocationRule().evaluate(examples);

        // 確認：例が違反になる
        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains(ExampleProperties.class.getName()));
    }

    @Test
    @DisplayName("モジュールの設定のクラス（@Configuration）は、infrastructure に置く")
    void moduleConfigurationResidesInInfrastructure() {
        // 操作と確認：本番の手書きのクラスに規則を当てる
        moduleConfigurationRule(ProductionClasses.MODULE_PACKAGE + "..").check(ProductionClasses.handwritten());
    }

    @Test
    @DisplayName("設定のクラスの規則は、infrastructure の外に置いた例に当たる")
    void configurationRuleMatchesMisplacedExample() {
        // 準備：infrastructure の外の設定のクラスの例
        var examples = new ClassFileImporter().importClasses(MisplacedExampleConfig.class);

        // 操作：例のパッケージに置き換えた規則を当てる
        var result = moduleConfigurationRule("com.ecsite.architecture.sample.configprops..").evaluate(examples);

        // 確認：例が違反になる
        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains(MisplacedExampleConfig.class.getName()));
    }

    @Test
    @DisplayName("presentation は、設定の値の型（@ConfigurationProperties）に依存しない")
    void presentationDoesNotDependOnConfigurationProperties() {
        // 操作と確認：本番の手書きのクラスに規則を当てる
        presentationRule(ProductionClasses.MODULE_PACKAGE + ".*.presentation..").check(ProductionClasses.handwritten());
    }

    @Test
    @DisplayName("presentation の規則は、設定の値の型を使う例に当たる")
    void presentationRuleMatchesExampleUsingProperties() {
        // 準備：設定の値の型と、それを受け取るコントローラーの例
        var examples = new ClassFileImporter().importClasses(ExampleProperties.class, PresentationExampleController.class);

        // 操作：例のパッケージに置き換えた規則を当てる
        var result = presentationRule("com.ecsite.architecture.sample.configprops..").evaluate(examples);

        // 確認：コントローラーの例が違反になる
        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains(PresentationExampleController.class.getName()));
    }

    @Test
    @DisplayName("設定の値の型と、その項目からたどれる型のうち、秘密の値の項目を持つものは、toString を宣言する")
    void secretPropertiesDeclareToString() {
        // 操作と確認：本番の手書きのクラスに規則を当てる
        secretPropertiesRule(ProductionClasses.handwritten()).check(ProductionClasses.handwritten());
    }

    @Test
    @DisplayName("toString の規則は、秘密の値の項目を持ち toString を宣言しない例に当たる")
    void toStringRuleMatchesExampleWithoutToString() {
        // 準備：秘密の値の項目を持ち、toString を宣言しない例
        var examples = new ClassFileImporter().importClasses(SecretWithoutToStringProperties.class);

        // 操作：規則を当てる
        var result = secretPropertiesRule(examples).evaluate(examples);

        // 確認：例が違反になる
        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains(SecretWithoutToStringProperties.class.getName()));
    }

    @Test
    @DisplayName("toString の規則は、入れ子の型が秘密の値の項目を持ち toString を宣言しない例に当たる")
    void toStringRuleMatchesNestedExampleWithoutToString() {
        // 準備：入れ子の型が秘密の値の項目を持ち、toString を宣言しない例
        var examples = new ClassFileImporter().importClasses(
                NestedSecretProperties.class, NestedSecretProperties.Credentials.class);

        // 操作：規則を当てる
        var result = secretPropertiesRule(examples).evaluate(examples);

        // 確認：入れ子の型が違反になる
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains(NestedSecretProperties.Credentials.class.getName()));
    }

    @Test
    @DisplayName("toString の規則は、Map の値の型が秘密の値の項目を持ち toString を宣言しない例に当たる")
    void toStringRuleMatchesMapValueExampleWithoutToString() {
        // 準備：Map の値の型が秘密の値の項目を持ち、toString を宣言しない例
        var examples = new ClassFileImporter().importClasses(
                MappedSecretProperties.class, MappedSecretProperties.Endpoint.class);

        // 操作：規則を当てる
        var result = secretPropertiesRule(examples).evaluate(examples);

        // 確認：Map の値の型が違反になる
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains(MappedSecretProperties.Endpoint.class.getName()));
    }

    @Test
    @DisplayName("toString の規則は、引数のある toString だけを書いた record の例に当たる")
    void toStringRuleMatchesExampleWithOnlyParameterizedToString() {
        // 準備：引数のある toString だけを書き、コンパイラが作る toString を使う例
        var examples = new ClassFileImporter().importClasses(SecretWithParameterizedToStringProperties.class);

        // 操作：規則を当てる
        var result = secretPropertiesRule(examples).evaluate(examples);

        // 確認：例が違反になる
        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(detail -> detail.contains(SecretWithParameterizedToStringProperties.class.getName()));
    }

    @Test
    @DisplayName("toString の規則は、自分で書いた final の toString を持つ record の例を対象にし、違反にしない")
    void toStringRuleDoesNotMatchRecordWithFinalToString() {
        // 準備：final の toString を書いた record の例
        var examples = new ClassFileImporter().importClasses(SecretWithFinalToStringProperties.class);

        // 操作と確認：規則の対象に入り、違反にならない
        assertThat(secretConfigurationTypeNames(examples)).containsExactly(SecretWithFinalToStringProperties.class.getName());
        assertThat(secretPropertiesRule(examples).evaluate(examples).hasViolation()).isFalse();
    }

    @Test
    @DisplayName("toString の規則は、record でないクラスで、自分で書いた final の toString を持つ例を対象にし、違反にしない")
    void toStringRuleDoesNotMatchClassWithFinalToString() {
        // 準備：final の toString を書いた、record でないクラスの例
        var examples = new ClassFileImporter().importClasses(SecretClassWithFinalToStringProperties.class);

        // 操作と確認：規則の対象に入り、違反にならない
        assertThat(secretConfigurationTypeNames(examples)).containsExactly(SecretClassWithFinalToStringProperties.class.getName());
        assertThat(secretPropertiesRule(examples).evaluate(examples).hasViolation()).isFalse();
    }

    @Test
    @DisplayName("toString の規則は、規則に合う例に当たらない")
    void toStringRuleDoesNotMatchCompliantExamples() {
        // 準備：toString を宣言する例（入れ子を含む）と、秘密の値の項目を持たない例（static の定数、enum の定数の名前だけが当たる例を含む）
        var examples = new ClassFileImporter().importClasses(SecretWithToStringProperties.class, ExampleProperties.class,
                NestedSecretSafeProperties.class, NestedSecretSafeProperties.MaskedCredentials.class,
                StaticConstantProperties.class, EnumFieldProperties.class, EnumFieldProperties.Kind.class);

        // 操作と確認：違反にならない
        assertThat(secretPropertiesRule(examples).evaluate(examples).hasViolation()).isFalse();
    }

    @Nested
    @DisplayName("秘密の値の項目の名前")
    class SecretFieldNames {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"secretKey", "apiKey", "privateKey", "signingKey", "key", "credentials", "clientCredential",
                "secretAccessKey", "webhookSecret", "accessToken", "apiPassword", "salt", "hmacKey", "masterKey",
                "stripeAPIKey", "apikey", "apiKeyV2", "signingKey2", "credentialsJson", "encryptionKey", "hmackey",
                "masterkey", "signingkey"})
        @DisplayName("secret、password、salt、token を含む名前と、語に分けたとき credential か key の条件に合う名前は、秘密の値の項目の名前")
        void detectsSecretFieldName(String name) {
            // 操作と確認：秘密の値の項目の名前と判定する
            assertThat(isSecretFieldName(name)).isTrue();
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"cacheKey", "objectKey", "idempotencyKey", "credentialsFile", "accessKeyId", "publicKey",
                "publishableKey", "primaryKey", "privateKeyPath", "apiKeyHeader", "signingKeyId",
                "value", "endpoint", "keyId", "monkeyBusiness", "credentialsPath", "credentialsDir"})
        @DisplayName("秘密の語を含まず、credential と key の条件に合わない名前は、秘密の値の項目の名前でない")
        void ignoresOtherFieldName(String name) {
            // 操作と確認：秘密の値の項目の名前と判定しない
            assertThat(isSecretFieldName(name)).isFalse();
        }
    }

    // ============================== 規則の補助 ==============================

    /**
     * 設定の型のうち、秘密の値の項目を自分で持つものが toString を宣言する規則を作る。
     *
     * <p>外側の型の既定の toString は入れ子の型の toString を呼ぶので、入れ子の型も対象にする。
     *
     * @param classes 設定の型を探すクラス
     * @return 規則
     */
    private static ArchRule secretPropertiesRule(JavaClasses classes) {
        Set<String> targets = secretConfigurationTypeNames(classes);
        return classes().that(DescribedPredicate.describe(
                        "are configuration types with a secret field", javaClass -> targets.contains(javaClass.getName())))
                .should(new ArchCondition<JavaClass>("toString を宣言する") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        // record のコンパイラが作る toString は、バイトコードの形で見分けて除く（final の有無では見分けない）
                        boolean declared = javaClass.getMethods().stream()
                                .anyMatch(method -> method.getName().equals("toString")
                                        && method.getRawParameterTypes().isEmpty()
                                        && !isCompilerGeneratedRecordToString(javaClass));
                        events.add(new SimpleConditionEvent(javaClass, declared,
                                "%s は秘密の値の項目を持つが、toString を宣言していない".formatted(javaClass.getName())));
                    }
                });
    }

    /**
     * 引数のない toString が、record のコンパイラが作ったものかを返す。
     *
     * <p>コンパイラが作る toString は、本体が {@code java.lang.runtime.ObjectMethods} を呼ぶ invokedynamic だけで、
     * 自分で書いた toString にはこの呼び出しがない。final の有無と行番号では、自分で書いたものと見分けられない。
     *
     * @param javaClass 対象のクラス
     * @return コンパイラが作った toString なら true
     */
    private static boolean isCompilerGeneratedRecordToString(JavaClass javaClass) {
        if (!javaClass.isRecord()) {
            return false;
        }
        String resource = javaClass.getName().substring(javaClass.getName().lastIndexOf('.') + 1) + ".class";
        try (var bytes = javaClass.reflect().getResourceAsStream(resource)) {
            if (bytes == null) {
                throw new IllegalStateException("クラスファイルが読めない: " + javaClass.getName());
            }
            return ClassFile.of().parse(bytes.readAllBytes()).methods().stream()
                    .filter(method -> method.methodName().equalsString("toString")
                            && method.methodType().equalsString("()Ljava/lang/String;"))
                    .flatMap(method -> method.code().stream())
                    .flatMap(CodeModel::elementStream)
                    .anyMatch(element -> element instanceof InvokeDynamicInstruction invoke
                            && invoke.invokedynamic().bootstrap().bootstrapMethod().reference().owner().asInternalName()
                            .equals("java/lang/runtime/ObjectMethods"));
        } catch (IOException e) {
            throw new UncheckedIOException("クラスファイルが読めない: " + javaClass.getName(), e);
        }
    }

    /**
     * 設定の型のうち、秘密の値の項目を自分で持つものの名前を返す。
     *
     * <p>static の項目（定数、enum の定数）は設定の値でないので数えない。
     *
     * @param classes 設定の型を探すクラス
     * @return 型の名前
     */
    private static Set<String> secretConfigurationTypeNames(JavaClasses classes) {
        return configurationTypes(classes).stream()
                .filter(javaClass -> javaClass.getFields().stream()
                        .filter(field -> !field.getModifiers().contains(JavaModifier.STATIC))
                        .anyMatch(field -> isSecretFieldName(field.getName())))
                .map(JavaClass::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * 設定の型（{@code @ConfigurationProperties} の型と、その項目からたどれる自分のコードの型）を返す。
     *
     * <p>項目の型は、コレクションと Map の要素の型（型引数）を含め、再帰的にたどる。static の項目は設定の値でないのでたどらない。
     *
     * @param classes 設定の型を探すクラス
     * @return 設定の型
     */
    private static List<JavaClass> configurationTypes(JavaClasses classes) {
        Map<String, JavaClass> found = new LinkedHashMap<>();
        Deque<JavaClass> pending = classes.stream()
                .filter(javaClass -> javaClass.isAnnotatedWith(ConfigurationProperties.class))
                .collect(Collectors.toCollection(ArrayDeque::new));
        while (!pending.isEmpty()) {
            JavaClass current = pending.pop();
            if (found.putIfAbsent(current.getName(), current) != null) {
                continue;
            }
            current.getFields().stream()
                    .filter(field -> !field.getModifiers().contains(JavaModifier.STATIC))
                    .flatMap(field -> field.getType().getAllInvolvedRawTypes().stream())
                    .filter(type -> type.getPackageName().startsWith(OWN_PACKAGE_PREFIX))
                    .forEach(pending::push);
        }
        return List.copyOf(found.values());
    }

    /**
     * 秘密の値の項目の名前かを返す。
     *
     * <p>secret・password・salt・token は、名前を小文字にして含むかで判定する。credential と key は、名前を語に分けて判定する。
     *
     * @param name 項目の名前
     * @return 秘密の値の項目なら true
     */
    private static boolean isSecretFieldName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (SECRET_WORDS.stream().anyMatch(lower::contains)) {
            return true;
        }
        List<String> words = splitWords(name);
        if (words.isEmpty()) {
            return false;
        }
        String last = words.get(words.size() - 1);
        boolean credential = words.stream().anyMatch(word -> word.equals("credential") || word.equals("credentials"))
                && !CREDENTIAL_LOCATION_WORDS.contains(last);
        boolean key = last.equals("key")
                && (words.size() == 1 || KEY_PREFIX_WORDS.contains(words.get(words.size() - 2)));
        boolean joinedKey = name.equals(lower)
                && KEY_PREFIX_WORDS.stream().anyMatch(prefix -> lower.equals(prefix + "key"));
        return credential || key || joinedKey;
    }

    /**
     * 名前を小文字の語に分ける。
     *
     * <p>小文字から大文字への変わり目と、大文字の並びの終わりで分ける。最後に付く数字と、{@code V2} のようなバージョンの語は除く。
     *
     * @param name 項目の名前
     * @return 小文字の語
     */
    private static List<String> splitWords(String name) {
        return Arrays.stream(name.split("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])"))
                .filter(word -> !word.matches("[vV]\\d+"))
                .map(word -> word.replaceAll("\\d+$", "").toLowerCase(Locale.ROOT))
                .filter(word -> !word.isEmpty())
                .toList();
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
     * 設定の型を、スキャンの範囲（アプリのクラスの {@code @ConfigurationPropertiesScan} の値で指定したパッケージの下）に置く規則を作る。
     *
     * @return 規則
     */
    private static ArchRule configurationPropertiesLocationRule() {
        String[] scanPackages = Arrays.stream(EcSiteApplication.class.getAnnotation(ConfigurationPropertiesScan.class).value())
                .map(basePackage -> basePackage + "..")
                .toArray(String[]::new);
        return classes().that().areAnnotatedWith(ConfigurationProperties.class)
                .should().resideInAnyPackage(scanPackages);
    }

    /**
     * メソッドに {@code @ConfigurationProperties} を付けない規則を作る。
     *
     * <p>{@code @Bean} のメソッドに付けると、スキャンの外で設定の型を登録できてしまうため。
     *
     * @return 規則
     */
    private static ArchRule methodConfigurationPropertiesRule() {
        return noMethods().should().beAnnotatedWith(ConfigurationProperties.class)
                .because("設定の型の登録は、スキャンにまとめるため");
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
