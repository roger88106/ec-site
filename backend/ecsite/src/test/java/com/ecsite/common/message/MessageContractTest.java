package com.ecsite.common.message;

import com.ecsite.ProductionClasses;
import com.ecsite.common.error.ErrorCodeProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * エラーの定義とメッセージのファイルの対応を確認する契約テスト（バックエンドの書き方の「エラーは XxxErrorCode と BusinessException」）。<br />
 * エラーの定義（{@link ErrorCodeProvider} の enum の定数）とメッセージのキーが過不足なく対応し、
 * キーが定数の名前から決まり、文の差し込みの名前と定数に書いた値の名前が一致することを確認する。
 */
class MessageContractTest {

    /**
     * 業務のエラーのキーの形：ecsite.<モジュール>.<名前>（入力の検証のキー ecsite.validation.* は別に確認する）
     */
    private static final Pattern ERROR_KEY = Pattern.compile("^ecsite\\.(?!validation\\.)([a-z]+)\\.[a-z0-9_]+$");

    /**
     * 文の中の差し込みの名前
     */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z]+)}");

    /**
     * メッセージのファイル
     */
    private static final Properties MESSAGES = new Properties();

    /**
     * すべてのエラーの定義の定数
     */
    private static final List<Enum<?>> ERROR_CODES = new ArrayList<>();

    /**
     * メッセージのファイルと、すべてのエラーの定義の定数を読む
     */
    @BeforeAll
    static void load() throws IOException {
        try (var stream = MessageContractTest.class.getResourceAsStream("/messages.properties")) {
            assertThat(stream).as("messages.properties").isNotNull();
            MESSAGES.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        ERROR_CODES.addAll(ProductionClasses.errorCodes());
        ProductionClasses.requireNotEmpty(ERROR_CODES, "エラーの定義の定数");
    }

    @Test
    @DisplayName("エラーの定義のキーは、モジュールの名前と定数の名前から決まる")
    void keyIsDerivedFromName() {
        List<String> mismatched = ERROR_CODES.stream()
                .filter(constant -> !keyOf(constant).equals(expectedKey(constant)))
                .map(constant -> constant.getDeclaringClass().getSimpleName() + "." + constant.name() + " → " + keyOf(constant))
                .toList();

        assertThat(mismatched).isEmpty();
    }

    @Test
    @DisplayName("すべてのエラーの定義に、メッセージのファイルの文がある")
    void everyErrorHasMessage() {
        List<String> missing = ERROR_CODES.stream()
                .map(MessageContractTest::keyOf)
                .filter(key -> !MESSAGES.containsKey(key))
                .toList();

        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("メッセージのファイルの業務のエラーのキーに、エラーの定義がある")
    void everyMessageHasError() {
        Set<String> defined = ERROR_CODES.stream().map(MessageContractTest::keyOf).collect(Collectors.toSet());
        Set<String> orphans = MESSAGES.stringPropertyNames().stream()
                .filter(key -> ERROR_KEY.matcher(key).matches())
                .filter(key -> !defined.contains(key))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(orphans).isEmpty();
    }

    @Test
    @DisplayName("文の差し込みの名前と、エラーの定義に書いた値の名前が一致する")
    void placeholdersMatchParameters() {
        List<String> mismatched = new ArrayList<>();
        for (Enum<?> constant : ERROR_CODES) {
            String text = MESSAGES.getProperty(keyOf(constant));
            if (text == null) {
                continue;
            }
            Set<String> inText = new TreeSet<>();
            Matcher matcher = PLACEHOLDER.matcher(text);
            while (matcher.find()) {
                inText.add(matcher.group(1));
            }
            Set<String> declared = new TreeSet<>(((ErrorCodeProvider) constant).getCode().message().paramKeys());
            if (!inText.equals(declared)) {
                mismatched.add(keyOf(constant) + "：文 " + inText + "、定義 " + declared);
            }
        }

        assertThat(mismatched).isEmpty();
    }

    /**
     * エラーの定義の定数が持つメッセージのキーを返す。
     *
     * @param constant エラーの定義の定数
     * @return メッセージのキー
     */
    private static String keyOf(Enum<?> constant) {
        return ((ErrorCodeProvider) constant).getCode().message().code();
    }

    /**
     * 規則から決まるキー（ecsite.<モジュール>.<定数の名前の小文字>）を返す。
     *
     * @param constant エラーの定義の定数
     * @return 規則から決まるキー
     */
    private static String expectedKey(Enum<?> constant) {
        // モジュールでない共通のものは common
        String module = ProductionClasses.moduleName(constant.getDeclaringClass().getPackageName()).orElse("common");
        return "ecsite.%s.%s".formatted(module, constant.name().toLowerCase(Locale.ROOT));
    }
}
