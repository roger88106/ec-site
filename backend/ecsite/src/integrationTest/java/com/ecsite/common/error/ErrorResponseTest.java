package com.ecsite.common.error;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.common.message.I18nMessage;
import com.ecsite.support.config.IntegrationTest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.ecsite.support.http.ProblemAssertions.problem;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 例外とエラーレスポンスの統合テスト
 *
 * <p>API の規約に対応する。
 * テスト用の Controller から各種の例外を発生させ、エラーレスポンスの形を確認する。
 */
@IntegrationTest
@Import({ErrorResponseTest.ErrorTestController.class, ErrorResponseTest.ValidatedTestController.class})
class ErrorResponseTest {

    // ============================== 定数 ==============================

    /**
     * エラーを起こすテスト用の Controller のパス
     */
    private static final String BASE = "/api/test-errors";

    /**
     * {@code @Validated} の Controller のパス
     *
     * <p>生成インターフェースは {@code @Validated} 付きなので、検証は Controller の AOP で行われる。
     */
    private static final String VALIDATED_BASE = "/api/test-validated";

    // 期待するメッセージ（messages.properties）。メッセージを変えたら、ここも合わせる
    /**
     * 入力の誤りのメッセージ
     */
    private static final String INVALID_REQUEST = "入力内容に誤りがあります。";

    /**
     * 見つからないときのメッセージ
     */
    private static final String NOT_FOUND = "見つかりませんでした。";

    /**
     * 同時更新のメッセージ
     */
    private static final String CONCURRENT_MODIFICATION = "他の人が先に更新しました。再読み込みしてください。";

    /**
     * 許可されていないメソッドのメッセージ
     */
    private static final String METHOD_NOT_ALLOWED = "この操作は許可されていません。";

    /**
     * システムエラーのメッセージ
     */
    private static final String SYSTEM_ERROR = "予期しないエラーが発生しました。";

    /**
     * 日本語（漢字・ひらがな・カタカナ）を含む
     */
    private static final String JAPANESE = "(?s).*[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}].*";

    // ============================== Bean ==============================

    /**
     * API を呼ぶ MockMvc
     */
    @Autowired
    MockMvc mockMvc;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("アプリが投げる例外")
    class ApplicationExceptions {

        @Test
        @DisplayName("業務ルール違反は 400。メッセージのパラメータが埋め込まれる")
        void businessRuleViolation() throws Exception {
            // 操作と確認：値「標準」を差し込んだ業務ルール違反の 400
            mockMvc.perform(get(BASE + "/business-rule"))
                    .andExpect(status().isBadRequest())
                    .andExpectAll(problem("TEST_BUSINESS_RULE", "テスト用の業務ルールに違反しました（標準）。"));
        }

        @Test
        @DisplayName("見つからない場合は 404")
        void notFound() throws Exception {
            // 操作と確認：NOT_FOUND の 404
            mockMvc.perform(get(BASE + "/not-found"))
                    .andExpect(status().isNotFound())
                    .andExpectAll(problem("NOT_FOUND", NOT_FOUND));
        }

        @Test
        @DisplayName("バージョン不一致は 409（CONCURRENT_MODIFICATION）")
        void concurrentModification() throws Exception {
            // 操作と確認：CONCURRENT_MODIFICATION の 409
            mockMvc.perform(get(BASE + "/conflict"))
                    .andExpect(status().isConflict())
                    .andExpectAll(problem("CONCURRENT_MODIFICATION", CONCURRENT_MODIFICATION));
        }

        @Test
        @DisplayName("システム例外は 500")
        void systemException() throws Exception {
            // 操作と確認：SYSTEM_ERROR の 500
            mockMvc.perform(get(BASE + "/system"))
                    .andExpect(status().isInternalServerError())
                    .andExpectAll(problem("SYSTEM_ERROR", SYSTEM_ERROR));
        }

        @Test
        @DisplayName("想定外の例外は 500")
        void unexpectedException() throws Exception {
            // 操作と確認：SYSTEM_ERROR の 500
            mockMvc.perform(get(BASE + "/unexpected"))
                    .andExpect(status().isInternalServerError())
                    .andExpectAll(problem("SYSTEM_ERROR", SYSTEM_ERROR));
        }
    }

    @Nested
    @DisplayName("入力検証")
    class Validation {

        @Test
        @DisplayName("リクエスト本文の検証エラーは 400。errors にフィールドごとの日本語のメッセージが入る")
        void requestBodyValidation() throws Exception {
            // 操作：name が空白、size が上限超えの本文を送る
            var result = mockMvc.perform(post(BASE + "/validation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name": " ", "size": 101}
                            """));

            // 確認：INVALID_REQUEST の 400 で、2 つの項目に日本語のメッセージ
            result.andExpect(status().isBadRequest())
                    .andExpectAll(problem("INVALID_REQUEST", INVALID_REQUEST))
                    .andExpect(jsonPath("$.errors", hasSize(2)))
                    // フィルター式の結果は配列になる
                    .andExpect(jsonPath("$.errors[?(@.field == 'name')].message").value(contains(matchesPattern(JAPANESE))))
                    .andExpect(jsonPath("$.errors[?(@.field == 'size')].message").value(contains(matchesPattern(JAPANESE))));
        }

        @Test
        @DisplayName("メソッド引数の検証エラー（size の上限超え）は 400。errors が入る")
        void methodParameterValidation() throws Exception {
            // 操作：size に上限を超える値を送る
            var result = mockMvc.perform(get(BASE + "/method-validation").param("size", "101"));

            // 確認：INVALID_REQUEST の 400 で、size に日本語のメッセージ
            assertSizeError(result);
        }

        @Test
        @DisplayName("@Validated の Controller（生成インターフェース）でのメソッド引数の検証エラーも 400。errors が入る")
        void methodParameterValidationOnValidatedController() throws Exception {
            // 操作：@Validated の Controller の size に上限を超える値を送る
            var result = mockMvc.perform(get(VALIDATED_BASE + "/method-validation").param("size", "101"));

            // 確認：INVALID_REQUEST の 400 で、size に日本語のメッセージ
            assertSizeError(result);
        }
    }

    @Nested
    @DisplayName("Spring MVC が投げる標準の例外")
    class FrameworkExceptions {

        @Test
        @DisplayName("パスの UUID の形式誤りは 400")
        void malformedUuidInPath() throws Exception {
            // 操作と確認：INVALID_REQUEST の 400
            mockMvc.perform(get(BASE + "/items/not-a-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpectAll(problem("INVALID_REQUEST", INVALID_REQUEST));
        }

        @Test
        @DisplayName("必須パラメータの欠落は 400")
        void missingRequiredParameter() throws Exception {
            // 操作と確認：INVALID_REQUEST の 400
            mockMvc.perform(get(BASE + "/required-param"))
                    .andExpect(status().isBadRequest())
                    .andExpectAll(problem("INVALID_REQUEST", INVALID_REQUEST));
        }

        @Test
        @DisplayName("パラメータの型の不一致は 400")
        void typeMismatch() throws Exception {
            // 操作と確認：数値の size に文字を送ると INVALID_REQUEST の 400
            mockMvc.perform(get(BASE + "/method-validation").param("size", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpectAll(problem("INVALID_REQUEST", INVALID_REQUEST));
        }

        @Test
        @DisplayName("JSON の構文誤りは 400")
        void malformedJson() throws Exception {
            // 操作と確認：途中で切れた JSON を送ると INVALID_REQUEST の 400
            mockMvc.perform(post(BASE + "/validation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": "))
                    .andExpect(status().isBadRequest())
                    .andExpectAll(problem("INVALID_REQUEST", INVALID_REQUEST));
        }

        @Test
        @DisplayName("存在しないパスは 404")
        void unknownPath() throws Exception {
            // 操作と確認：NOT_FOUND の 404
            mockMvc.perform(get(BASE + "/does-not-exist"))
                    .andExpect(status().isNotFound())
                    .andExpectAll(problem("NOT_FOUND", NOT_FOUND));
        }

        @Test
        @DisplayName("Spring MVC の標準の例外でも 5xx になるもの（パスの変数の名前の誤り）は、想定外のエラーの 500")
        void frameworkServerError() throws Exception {
            // 操作と確認：Controller の定義の誤りは、入力の誤りの本文でなく SYSTEM_ERROR の 500
            mockMvc.perform(get(BASE + "/missing-path-variable/value"))
                    .andExpect(status().isInternalServerError())
                    .andExpectAll(problem("SYSTEM_ERROR", SYSTEM_ERROR));
        }

        @Test
        @DisplayName("許可されていないメソッドは 405")
        void methodNotAllowed() throws Exception {
            // 操作と確認：GET だけのパスに DELETE を送ると METHOD_NOT_ALLOWED の 405
            mockMvc.perform(delete(BASE + "/not-found"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpectAll(problem("METHOD_NOT_ALLOWED", METHOD_NOT_ALLOWED));
        }
    }

    @Test
    @DisplayName("エラーの応答に、リクエストのトレースの traceId（32 桁の 16 進数）を入れる")
    void returnsTraceId() throws Exception {
        // 操作：エラーになるパスを呼ぶ
        ResultActions result = mockMvc.perform(get(BASE + "/not-found"));

        // 確認：32 桁の 16 進数の traceId
        result.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{32}")));
    }

    // ============================== 確認の補助 ==============================

    /**
     * size の上限超えの検証エラーの応答であることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>状態が 400</li>
     *   <li>エラーコードとメッセージが入力の誤り</li>
     *   <li>errors の最初の項目が size</li>
     *   <li>その項目のメッセージが日本語</li>
     * </ul>
     *
     * @param result 応答
     * @throws Exception 応答を読めないとき
     */
    private static void assertSizeError(ResultActions result) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpectAll(problem("INVALID_REQUEST", INVALID_REQUEST))
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].message").value(matchesPattern(JAPANESE)));
    }

    // ============================== テスト用の Controller ==============================

    /**
     * テスト用のエラーコード
     */
    enum TestErrorCode implements ErrorCodeProvider {
        TEST_BUSINESS_RULE;

        @Override
        public ErrorCode getCode() {
            return ErrorCode.of(name(), ErrorType.BUSINESS_RULE_ERROR,
                    I18nMessage.of("ecsite.test.business_rule", List.of("name")));
        }
    }

    /**
     * リクエスト本文
     *
     * @param name 名前
     * @param size 件数
     */
    record ValidationRequest(@NotBlank String name, @Max(100) Integer size) {
    }

    /**
     * 各種の例外を発生させる Controller
     */
    @RestController
    @RequestMapping(BASE)
    static class ErrorTestController {

        @GetMapping("/business-rule")
        void businessRule() {
            throw BusinessException.of(TestErrorCode.TEST_BUSINESS_RULE, List.of("標準"));
        }

        @GetMapping("/not-found")
        void notFound() {
            throw BusinessException.of(CommonErrorCode.NOT_FOUND);
        }

        @GetMapping("/conflict")
        void conflict() {
            throw BusinessException.of(CommonErrorCode.CONCURRENT_MODIFICATION);
        }

        @GetMapping("/system")
        void system() {
            throw SystemException.of("テスト用のシステム例外", new IllegalStateException("原因"));
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("想定外");
        }

        @PostMapping("/validation")
        void validation(@Valid @RequestBody ValidationRequest request) {
        }

        @GetMapping("/method-validation")
        void methodValidation(@RequestParam @Max(100) Integer size) {
        }

        @GetMapping("/items/{id}")
        void item(@PathVariable UUID id) {
        }

        @GetMapping("/required-param")
        void requiredParam(@RequestParam String q) {
        }

        @GetMapping("/missing-path-variable/{actual}")
        void missingPathVariable(@PathVariable("expected") String expected) {
        }
    }

    /**
     * {@code @Validated} の Controller
     *
     * <p>生成インターフェースと同じく、メソッド引数の検証が AOP で行われる。
     */
    @RestController
    @RequestMapping(VALIDATED_BASE)
    @Validated
    static class ValidatedTestController {

        @GetMapping("/method-validation")
        void methodValidation(@RequestParam @Max(100) Integer size) {
        }
    }
}
