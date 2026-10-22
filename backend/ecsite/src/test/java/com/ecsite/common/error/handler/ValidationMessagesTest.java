package com.ecsite.common.error.handler;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.ErrorDetail;
import com.ecsite.common.error.ValidationErrorMapping;
import com.ecsite.common.message.MessageHelper;
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
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.validation.method.ParameterValidationResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 入力の検証のメッセージの単体テスト（docs/rules/backend-coding.md 第 3.3 節、spec 005 DC-37）。<br />
 * 制約の種類から ecsite.validation.* の文を選び、制約の値を差し込む。JVM と要求のロケールによらず、同じ日本語になる。
 */
class ValidationMessagesTest {

    /**
     * 検証する型（項目ごとに 1 つの制約）
     */
    // 配列に付けた @Size の検証も確認するので、配列の成分が必要だ（直すと確認する内容が変わる）
    @SuppressWarnings("ArrayRecordComponent")
    record Sample(
            @NotNull String notNull,
            @NotBlank String notBlank,
            @Size(min = 1, max = 50) String sized,
            @Size(max = 10000) String maxSized,
            @Min(0) Long min,
            @Max(100) Integer max,
            @DecimalMin("0.5") BigDecimal decimalMin,
            @DecimalMax("99.9") BigDecimal decimalMax,
            @Pattern(regexp = "^[A-Z]+$") String pattern,
            @Email String email,
            @Future LocalDate future,
            @Size(min = 1) List<String> items,
            @Size(max = 3) List<String> fewItems,
            @Size(min = 1, max = 3) String[] someItems) {
    }

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
    private ValidationMessages messages;

    /**
     * メッセージの取り出し
     */
    private MessageHelper helper;

    /**
     * JVM と要求のロケールを英語にし、メッセージのファイルを読む部品を作る
     */
    @BeforeEach
    void setUp() {
        originalLocale = Locale.getDefault();
        // 実行環境のロケールと要求のロケールが日本語でなくても、日本語になることを確認する
        Locale.setDefault(Locale.US);
        LocaleContextHolder.setLocale(Locale.US);
        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        helper = new MessageHelper(messageSource);
        messages = new ValidationMessages(helper, List.of());
    }

    /**
     * ロケールを元に戻す
     */
    @AfterEach
    void tearDown() {
        Locale.setDefault(originalLocale);
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("必須（NotNull・NotBlank）は「入力してください。」")
    void required() {
        assertThat(messageOf("notNull", null)).isEqualTo("入力してください。");
        assertThat(messageOf("notBlank", " ")).isEqualTo("入力してください。");
    }

    @Test
    @DisplayName("長さは、上限と（1 以上なら）下限を差し込む。数は 3 桁ごとに区切る")
    void size() {
        assertThat(messageOf("sized", "x".repeat(51))).isEqualTo("1 文字以上 50 文字以内にしてください。");
        assertThat(messageOf("maxSized", "x".repeat(10001))).isEqualTo("10,000 文字以内にしてください。");
    }

    @Test
    @DisplayName("配列・一覧の長さは、文字数ではなく件数の文にする")
    void sizeOfItems() {
        assertThat(messageOf("items", List.of())).isEqualTo("1 件以上にしてください。");
        assertThat(messageOf("fewItems", List.of("a", "b", "c", "d"))).isEqualTo("3 件以内にしてください。");
        assertThat(messageOf("someItems", new String[0])).isEqualTo("1 件以上 3 件以内にしてください。");
    }

    @Test
    @DisplayName("範囲は、下限・上限の値を差し込む")
    void range() {
        assertThat(messageOf("min", -1L)).isEqualTo("0 以上にしてください。");
        assertThat(messageOf("max", 101)).isEqualTo("100 以下にしてください。");
        assertThat(messageOf("decimalMin", new BigDecimal("0.1"))).isEqualTo("0.5 以上にしてください。");
        assertThat(messageOf("decimalMax", new BigDecimal("100"))).isEqualTo("99.9 以下にしてください。");
    }

    @Test
    @DisplayName("形式（Pattern・Email）は、形式が正しくないことを示す")
    void format() {
        assertThat(messageOf("pattern", "abc")).isEqualTo("形式が正しくありません。");
        assertThat(messageOf("email", "not-an-email")).isEqualTo("メールアドレスの形式が正しくありません。");
    }

    @Test
    @DisplayName("文を用意していない種類の制約は「値が正しくありません。」")
    void unknownConstraint() {
        assertThat(messageOf("future", LocalDate.of(2000, 1, 1))).isEqualTo("値が正しくありません。");
    }

    @Test
    @DisplayName("Bean Validation の違反でない検証エラー（型の変換の失敗など）は「値が正しくありません。」")
    void notConstraintViolation() {
        assertThat(messages.of(new ObjectError("request", "Failed to convert"))).isEqualTo("値が正しくありません。");
    }

    @Test
    @DisplayName("メソッド引数の検証エラーで、Bean Validation の違反でないものは「値が正しくありません。」")
    void parameterErrorWithoutConstraintViolation() throws NoSuchMethodException {
        var parameter = new MethodParameter(ValidationMessagesTest.class.getDeclaredMethod("messageOf", String.class, Object.class), 0);
        var error = new DefaultMessageSourceResolvable("typeMismatch");
        var result = new ParameterValidationResult(parameter, "x", List.of(error), null, null, null,
                (resolvable, type) -> null);

        assertThat(messages.of(result, error)).isEqualTo("値が正しくありません。");
    }

    @Test
    @DisplayName("Bean Validation の違反から作った検証エラーは、制約の種類の文になる")
    void fromSpringValidator() {
        var target = new Sample(null, "x", "x", "x", 0L, 0, BigDecimal.ONE, BigDecimal.ONE, "A", "a@b.jp", null, List.of("x"), List.of(), new String[]{"x"});
        var errors = new BeanPropertyBindingResult(target, "sample");
        new SpringValidatorAdapter(validator).validate(target, errors);

        assertThat(messages.of(errors.getFieldError("notNull"))).isEqualTo("入力してください。");
    }

    @Test
    @DisplayName("業務のエラーに対応づけた項目は、制約の種類の文の代わりに業務のエラーの文になる。ほかの項目は種類の文のまま")
    void mappedField() {
        ValidationErrorMapping mapping = () -> Map.of("pattern", ErrorDetail.of(CommonErrorCode.INVALID_MONEY.getCode(), List.of(0)));
        var mapped = new ValidationMessages(helper, List.of(mapping));
        var target = new Sample("x", "x", "x", "x", 0L, 0, BigDecimal.ONE, BigDecimal.ONE, "abc", "x", null, List.of("x"), List.of(), new String[]{"x"});
        var errors = new BeanPropertyBindingResult(target, "sample");
        new SpringValidatorAdapter(validator).validate(target, errors);

        assertThat(mapped.of(errors.getFieldError("pattern"))).isEqualTo("金額は 0 円以上にしてください。");
        assertThat(mapped.of(errors.getFieldError("email"))).isEqualTo("メールアドレスの形式が正しくありません。");
    }

    @Test
    @DisplayName("同じ項目のパスを複数の対応が対応づけていれば、作るときに失敗する")
    void duplicateMapping() {
        ValidationErrorMapping mapping = () -> Map.of("pattern", ErrorDetail.of(CommonErrorCode.INVALID_MONEY.getCode(), List.of(0)));

        assertThatThrownBy(() -> new ValidationMessages(helper, List.of(mapping, mapping)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pattern");
    }

    /**
     * 1 つの項目だけに値を入れた見本を検証し、その項目の違反の文を返す。
     *
     * @param property 項目の名前
     * @param value    値
     * @return 違反の文
     */
    private String messageOf(String property, Object value) {
        ConstraintViolation<Sample> violation = validator.validateValue(Sample.class, property, value).iterator().next();
        return messages.of(violation);
    }
}
