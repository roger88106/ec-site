package com.ecsite.common.message;

import com.ecsite.common.error.ErrorCodeProvider;
import com.ecsite.support.ProductionClasses;
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
 * エラーの定義とメッセージのファイルの対応を確認する契約テスト
 *
 * <p>エラーの定義（{@link ErrorCodeProvider} の enum の定数）とメッセージのキーが過不足なく対応し、
 * キーが定数の名前から決まり、文の差し込みの名前と定数に書いた値の名前が一致することを確認する。
 * 文に上限などの値を直接書いていないことも確認する（値が変わったときに、文が取り残されないように）。
 * 文言の書き方の規則で使わないと決めた言い回しが、メールの文を除く文にないことも確認する。
 */
class MessageContractTest {

    // ============================== 定数 ==============================

    /**
     * 業務のエラーのキーの形：ecsite.<モジュール>.<名前>（入力の検証のキー ecsite.validation.* は別に確認する）
     */
    private static final Pattern ERROR_KEY = Pattern.compile("^ecsite\\.(?!validation\\.)([a-z]+)\\.[a-z0-9_]+$");

    /**
     * 文の中の差し込みの名前
     */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z]+)}");

    /**
     * 文の中の数字（半角と全角）
     */
    private static final Pattern NUMBER = Pattern.compile("[0-9０-９]+");

    /**
     * 文に直接書いてよい数字。「1 文字以上」「0 以外」のように、空でないことや数え方を言う 0 と 1 は、変わる規則の値でないため
     */
    private static final Pattern COUNTING_WORDS = Pattern.compile("[01０１]");

    /**
     * 文に使わない言い回し（規則の番号は、バックエンドの書き方の「メッセージの文言の書き方」）
     *
     * <p>規則 4 の言い換えを求める語は、scripts/ci/ja/ja-text.json で「確認する」に言い換える語と合わせる。
     */
    private static final List<String> FORBIDDEN_PHRASES = List.of(
            "で入力してください", "で指定してください", // 規則 1
            "形が", "確かめ", // 規則 4
            "C 側"); // 規則 5

    /**
     * 数字の検査と、使わない言い回しの検査で見ないキーの接頭辞（メールの文。画面に表示する文でない）
     */
    private static final String NOTIFICATION_PREFIX = "ecsite.notification.";

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

    // ============================== テスト ==============================

    @Test
    @DisplayName("エラーの定義のキーは、モジュールの名前と定数の名前から決まる")
    void keyIsDerivedFromName() {
        // 操作：キーが規則から決まるキーと違う定数を集める
        List<String> mismatched = ERROR_CODES.stream()
                .filter(constant -> !keyOf(constant).equals(expectedKey(constant)))
                .map(constant -> constant.getDeclaringClass().getSimpleName() + "." + constant.name() + " → " + keyOf(constant))
                .toList();

        // 確認：ない
        assertThat(mismatched).isEmpty();
    }

    @Test
    @DisplayName("すべてのエラーの定義に、メッセージのファイルの文がある")
    void everyErrorHasMessage() {
        // 操作：メッセージのファイルにないキーを集める
        List<String> missing = ERROR_CODES.stream()
                .map(MessageContractTest::keyOf)
                .filter(key -> !MESSAGES.containsKey(key))
                .toList();

        // 確認：ない
        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("メッセージのファイルの業務のエラーのキーに、エラーの定義がある")
    void everyMessageHasError() {
        // 操作：業務のエラーの形のキーのうち、エラーの定義がないものを集める
        Set<String> defined = ERROR_CODES.stream().map(MessageContractTest::keyOf).collect(Collectors.toSet());
        Set<String> orphans = MESSAGES.stringPropertyNames().stream()
                .filter(key -> ERROR_KEY.matcher(key).matches())
                .filter(key -> !defined.contains(key))
                .collect(Collectors.toCollection(TreeSet::new));

        // 確認：ない
        assertThat(orphans).isEmpty();
    }

    @Test
    @DisplayName("文の差し込みの名前と、エラーの定義に書いた値の名前が一致する")
    void placeholdersMatchParameters() {
        // 操作：文の差し込みの名前と、定義の値の名前が違う定数を集める（文のない定数は別のテストが見る）
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

        // 確認：ない
        assertThat(mismatched).isEmpty();
    }

    @Test
    @DisplayName("エラーの文に、差し込みでない 2 以上の数字を書かない")
    void noLiteralLimitInMessage() {
        // 操作：メールの文を除いた文から差し込みを除き、0 と 1 以外の数字を含む文を集める
        List<String> literal = MESSAGES.stringPropertyNames().stream()
                .filter(key -> !key.startsWith(NOTIFICATION_PREFIX))
                .sorted()
                .filter(key -> NUMBER.matcher(PLACEHOLDER.matcher(MESSAGES.getProperty(key)).replaceAll("")).results()
                        .anyMatch(number -> !COUNTING_WORDS.matcher(number.group()).matches()))
                .map(key -> key + "：" + MESSAGES.getProperty(key))
                .toList();

        // 確認：ない（上限などの値は、ドメインの定数から {名前} で差し込む）
        assertThat(literal).isEmpty();
    }

    @Test
    @DisplayName("エラーの文に、使わない言い回しを書かない")
    void noForbiddenPhraseInMessage() {
        // 操作：メールの文を除いた文から、使わない言い回しを含む文を集める
        List<String> found = MESSAGES.stringPropertyNames().stream()
                .filter(key -> !key.startsWith(NOTIFICATION_PREFIX))
                .sorted()
                .filter(key -> FORBIDDEN_PHRASES.stream().anyMatch(MESSAGES.getProperty(key)::contains))
                .map(key -> key + "：" + MESSAGES.getProperty(key))
                .toList();

        // 確認：ない（書き方は、バックエンドの書き方の「メッセージの文言の書き方」）
        assertThat(found).isEmpty();
    }

    // ============================== 補助 ==============================

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
