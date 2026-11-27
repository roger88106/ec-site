package com.ecsite.common.error.handler;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.ErrorDetail;
import com.ecsite.common.error.ValidationErrorMapping;
import com.ecsite.common.message.MessageResolver;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.validation.method.ParameterValidationResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 入力の検証のメッセージの単体テスト
 *
 * <p>制約の種類から ecsite.validation.* の文を選び、制約の値を差し込む。JVM とリクエストのロケールによらず、同じ日本語になる。
 */
class ValidationErrorTranslatorTest {

    // ============================== 定数 ==============================

    /**
     * sized の上限（見本の型 Sample の制約の値。見本は本番の定数を持たないため、テストの中で決める）
     */
    private static final int SIZED_MAX = 50;

    /**
     * sized の下限（見本の型 Sample の制約の値。見本は本番の定数を持たないため、テストの中で決める）
     */
    private static final int SIZED_MIN = 1;

    /**
     * maxSized の上限（見本の型 Sample の制約の値。見本は本番の定数を持たないため、テストの中で決める）
     */
    private static final int MAX_SIZED = 10000;

    /**
     * max の上限（見本の型 Sample の制約の値。見本は本番の定数を持たないため、テストの中で決める）
     */
    private static final int MAX_VALUE = 100;

    /**
     * 必須の項目の文
     */
    private static final String REQUIRED = "入力してください。";

    /**
     * 文を用意していない誤りの文
     */
    private static final String INVALID_VALUE = "値が正しくありません。";

    /**
     * メールアドレスの形の誤りの文
     */
    private static final String INVALID_EMAIL = "メールアドレスの形式が正しくありません。";

    /**
     * 業務のエラーに対応づける項目
     */
    private static final String MAPPED_PROPERTY = "pattern";

    /**
     * 項目を業務のエラー（金額が負）に対応づける対応
     */
    private static final ValidationErrorMapping MAPPING =
            () -> Map.of(MAPPED_PROPERTY, ErrorDetail.of(CommonErrorCode.INVALID_MONEY.getCode(), List.of(0)));

    /**
     * 検証する型（項目ごとに 1 つの制約）
     */
    record Sample(
            @NotNull String notNull,
            @NotBlank String notBlank,
            @Size(min = SIZED_MIN, max = SIZED_MAX) String sized,
            @Size(max = MAX_SIZED) String maxSized,
            @Min(0) Long min,
            @Max(MAX_VALUE) Integer max,
            @DecimalMin("0.5") BigDecimal decimalMin,
            @DecimalMax("99.9") BigDecimal decimalMax,
            @Pattern(regexp = "^[A-Z]+$") String pattern,
            @Email String email,
            @Future LocalDate future,
            @Size(min = 1) List<String> items,
            @Size(max = 3) List<String> fewItems,
            @Size(min = 1, max = 3) String[] someItems) {
    }

    // ============================== 対象 ==============================

    /**
     * 検証の部品（Hibernate Validator）
     */
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /**
     * テストの前の JVM の既定のロケール
     */
    private Locale originalLocale;

    /**
     * テストの対象
     */
    private ValidationErrorTranslator messages;

    /**
     * メッセージの取り出し
     */
    private MessageResolver resolver;

    /**
     * JVM とリクエストのロケールを英語にし、メッセージのファイルを読む部品を作る
     */
    @BeforeEach
    void setUp() {
        originalLocale = Locale.getDefault();
        // 実行環境のロケールとリクエストのロケールが日本語でなくても、日本語になることを確認する
        Locale.setDefault(Locale.US);
        LocaleContextHolder.setLocale(Locale.US);
        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        resolver = new MessageResolver(messageSource);
        messages = new ValidationErrorTranslator(resolver, List.of());
    }

    /**
     * ロケールを元に戻す
     */
    @AfterEach
    void tearDown() {
        Locale.setDefault(originalLocale);
        LocaleContextHolder.resetLocaleContext();
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("制約の種類ごとの文")
    class ByConstraint {

        @Test
        @DisplayName("NotNull の違反は「入力してください。」")
        void notNull() {
            // 操作と確認：null の違反の文
            assertThat(messageOf("notNull", null)).isEqualTo(REQUIRED);
        }

        @Test
        @DisplayName("NotBlank の違反は「入力してください。」")
        void notBlank() {
            // 操作と確認：空白だけの値の違反の文
            assertThat(messageOf("notBlank", " ")).isEqualTo(REQUIRED);
        }

        @Test
        @DisplayName("下限が 1 以上の長さの下限は、下限と上限を差し込み、下限ちょうどは通り、下限の 1 つ外は違反の文")
        void sizeWithMinLowerBound() {
            // 操作と確認：1〜50 文字の項目は、1 文字（下限ちょうど）で違反しない
            assertThat(violationsOf("sized", "x".repeat(SIZED_MIN))).isEmpty();
            // 操作と確認：下限の 1 つ外（0 文字）の違反の文
            assertThat(messageOf("sized", "x".repeat(SIZED_MIN - 1))).isEqualTo("1 文字以上 50 文字以内にしてください。");
        }

        @Test
        @DisplayName("下限が 1 以上の長さの上限は、下限と上限を差し込み、上限ちょうどは通り、上限の 1 つ外は違反の文")
        void sizeWithMinUpperBound() {
            // 操作と確認：1〜50 文字の項目は、50 文字（上限ちょうど）で違反しない
            assertThat(violationsOf("sized", "x".repeat(SIZED_MAX))).isEmpty();
            // 操作と確認：上限の 1 つ外（51 文字）の違反の文
            assertThat(messageOf("sized", "x".repeat(SIZED_MAX + 1))).isEqualTo("1 文字以上 50 文字以内にしてください。");
        }

        @Test
        @DisplayName("下限のない長さの上限は、上限だけを差し込み、数は 3 桁ごとに区切り、上限ちょうどは通る")
        void sizeWithoutMinUpperBound() {
            // 操作と確認：10,000 文字（上限ちょうど）で違反しない
            assertThat(violationsOf("maxSized", "x".repeat(MAX_SIZED))).isEmpty();
            // 操作と確認：上限の 1 つ外（10,001 文字）の違反の文
            assertThat(messageOf("maxSized", "x".repeat(MAX_SIZED + 1))).isEqualTo("10,000 文字以内にしてください。");
        }

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("com.ecsite.common.error.handler.ValidationErrorTranslatorTest#itemSizes")
        @DisplayName("配列・一覧の長さは、文字数ではなく件数の文にする")
        void sizeOfItems(String property, Object value, String expected) {
            // 操作と確認：件数の文
            assertThat(messageOf(property, value)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "「{0}」")
        @MethodSource("com.ecsite.common.error.handler.ValidationErrorTranslatorTest#ranges")
        @DisplayName("範囲は、下限・上限の値を差し込む")
        void range(String property, Object value, String expected) {
            // 操作と確認：下限か上限の値を差し込んだ文
            assertThat(messageOf(property, value)).isEqualTo(expected);
        }

        @Test
        @DisplayName("Pattern の違反は、形式が正しくないことを示す")
        void pattern() {
            // 操作と確認：形式の誤りの文
            assertThat(messageOf("pattern", "abc")).isEqualTo("形式が正しくありません。");
        }

        @Test
        @DisplayName("Email の違反は、メールアドレスの形式が正しくないことを示す")
        void email() {
            // 操作と確認：メールアドレスの形式の誤りの文
            assertThat(messageOf("email", "not-an-email")).isEqualTo(INVALID_EMAIL);
        }

        @Test
        @DisplayName("文を用意していない種類の制約は「値が正しくありません。」")
        void unknownConstraint() {
            // 操作と確認：Future の違反は、文を用意していない種類
            assertThat(messageOf("future", LocalDate.of(2000, 1, 1))).isEqualTo(INVALID_VALUE);
        }
    }

    @Nested
    @DisplayName("Bean Validation の違反でない検証エラー")
    class NotConstraintViolation {

        @Test
        @DisplayName("Bean Validation の違反でない検証エラー（型の変換の失敗など）は「値が正しくありません。」")
        void notConstraintViolation() {
            // 操作と確認：型の変換の失敗の文
            assertThat(messages.messageOf(new ObjectError("request", "Failed to convert"))).isEqualTo(INVALID_VALUE);
        }

        @Test
        @DisplayName("メソッド引数の検証エラーで、Bean Validation の違反でないものは「値が正しくありません。」")
        void parameterErrorWithoutConstraintViolation() throws NoSuchMethodException {
            // 準備：型の不一致のメソッド引数の検証エラー
            var parameter = new MethodParameter(ValidationErrorTranslatorTest.class.getDeclaredMethod("messageOf", String.class, Object.class), 0);
            var error = new DefaultMessageSourceResolvable("typeMismatch");
            var result = new ParameterValidationResult(parameter, "x", List.of(error), null, null, null,
                    (resolvable, type) -> null);

            // 操作と確認：文を用意していない誤りの文
            assertThat(messages.messageOf(result, error)).isEqualTo(INVALID_VALUE);
        }
    }

    @Nested
    @DisplayName("Spring の検証の結果")
    class SpringValidation {

        @Test
        @DisplayName("Bean Validation の違反から作った検証エラーは、制約の種類の文になる")
        void fromSpringValidator() {
            // 準備：notNull だけが違反する値を、Spring の検証（SpringValidatorAdapter）で検証する
            var errors = validate(new Sample(null, "x", "x", "x", 0L, 0, BigDecimal.ONE, BigDecimal.ONE, "A", "a@b.jp", null,
                    List.of("x"), List.of(), new String[]{"x"}));

            // 操作と確認：NotNull の文
            assertThat(messages.messageOf(errors.getFieldError("notNull"))).isEqualTo(REQUIRED);
        }

        @Test
        @DisplayName("業務のエラーに対応づけた項目は、制約の種類の文の代わりに業務のエラーの文になる")
        void mappedField() {
            // 準備：対応づけた項目と email が違反する値を検証する
            var mapped = new ValidationErrorTranslator(resolver, List.of(MAPPING));
            var errors = validate(sampleWithMappedAndEmailViolations());

            // 操作と確認：業務のエラー（金額が負）の文
            assertThat(mapped.messageOf(errors.getFieldError(MAPPED_PROPERTY))).isEqualTo("金額は 0 円以上にしてください。");
        }

        @Test
        @DisplayName("業務のエラーに対応づけていない項目は、制約の種類の文のまま")
        void unmappedField() {
            // 準備：対応づけた項目と email が違反する値を検証する
            var mapped = new ValidationErrorTranslator(resolver, List.of(MAPPING));
            var errors = validate(sampleWithMappedAndEmailViolations());

            // 操作と確認：Email の文
            assertThat(mapped.messageOf(errors.getFieldError("email"))).isEqualTo(INVALID_EMAIL);
        }

        @Test
        @DisplayName("同じ項目のパスを複数の対応が対応づけていれば、作るときに失敗する")
        void duplicateMapping() {
            // 操作と確認：項目のパスを書いた IllegalStateException
            assertThatThrownBy(() -> new ValidationErrorTranslator(resolver, List.of(MAPPING, MAPPING)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(MAPPED_PROPERTY);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 配列・一覧の長さの見本を返す。
     *
     * @return 項目の名前、違反する値、期待する文
     */
    static Stream<Arguments> itemSizes() {
        return Stream.of(
                Arguments.of("items", List.of(), "1 件以上にしてください。"),
                Arguments.of("fewItems", List.of("a", "b", "c", "d"), "3 件以内にしてください。"),
                Arguments.of("someItems", new String[0], "1 件以上 3 件以内にしてください。"));
    }

    /**
     * 範囲の見本を返す。
     *
     * @return 項目の名前、違反する値、期待する文
     */
    static Stream<Arguments> ranges() {
        return Stream.of(
                Arguments.of("min", -1L, "0 以上にしてください。"),
                Arguments.of("max", MAX_VALUE + 1, "100 以下にしてください。"),
                Arguments.of("decimalMin", new BigDecimal("0.1"), "0.5 以上にしてください。"),
                Arguments.of("decimalMax", new BigDecimal("100"), "99.9 以下にしてください。"));
    }

    /**
     * 対応づけた項目（pattern）と email だけが違反する見本を作る。
     *
     * @return 見本
     */
    private static Sample sampleWithMappedAndEmailViolations() {
        return new Sample("x", "x", "x", "x", 0L, 0, BigDecimal.ONE, BigDecimal.ONE, "abc", "x", null,
                List.of("x"), List.of(), new String[]{"x"});
    }

    /**
     * 見本を Spring の検証（SpringValidatorAdapter）で検証する。
     *
     * @param target 見本
     * @return 検証の結果
     */
    private Errors validate(Sample target) {
        var errors = new BeanPropertyBindingResult(target, "sample");
        new SpringValidatorAdapter(validator).validate(target, errors);
        return errors;
    }

    /**
     * 1 つの項目だけに値を入れた見本を検証し、その項目の違反の文を返す。
     *
     * @param property 項目の名前
     * @param value    値
     * @return 違反の文
     */
    private String messageOf(String property, Object value) {
        ConstraintViolation<Sample> violation = violationsOf(property, value).iterator().next();
        return messages.messageOf(violation);
    }

    /**
     * 1 つの項目だけに値を入れた見本を検証し、違反の一覧を返す（違反がなければ空）。
     *
     * @param property 項目の名前
     * @param value    値
     * @return 違反の一覧
     */
    private Set<ConstraintViolation<Sample>> violationsOf(String property, Object value) {
        return validator.validateValue(Sample.class, property, value);
    }
}
