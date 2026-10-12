package com.ecsite.common.error.handler;

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
import org.springframework.context.support.ResourceBundleMessageSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 入力の検証のメッセージの単体テスト（docs/style/backend.md 第 3.5 節、spec 005 DC-37）。<br />
 * 制約の種類から ecsite.validation.* の文を選び、制約の値を差し込む。JVM と要求のロケールによらず、同じ日本語になる。
 */
class ValidationMessagesTest {

    /**
     * 検証する型（項目ごとに 1 つの制約）
     */
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
            @Future LocalDate future) {
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
     * JVM と要求のロケールを英語にし、メッセージのファイルを読む部品を作る
     */
    @BeforeEach
    void setUp() {
        originalLocale = Locale.getDefault();
        // 実行環境のロケールと要求のロケールが日本語でなくても、日本語になることを確かめる
        Locale.setDefault(Locale.US);
        LocaleContextHolder.setLocale(Locale.US);
        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messages = new ValidationMessages(new MessageHelper(messageSource));
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
