package com.ecsite.common.error;

import com.ecsite.IntegrationTest;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.error.exception.SystemException;
import com.ecsite.common.message.I18nMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 例外とエラーレスポンスの統合テスト（T010、constitution 9）。<br />
 * テスト用の Controller から各種の例外を発生させ、エラーレスポンスの形を確かめる。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import({ErrorResponseTest.ErrorTestController.class, ErrorResponseTest.ValidatedTestController.class})
class ErrorResponseTest {

    /**
     * エラーを起こすテスト用の Controller のパス
     */
    private static final String BASE = "/api/test-errors";

    /**
     * @Validated の Controller のパス。生成インターフェースは @Validated 付きなので、検証は Controller の AOP で行われる
     */
    private static final String VALIDATED_BASE = "/api/test-validated";

    // 期待するメッセージ（messages.properties）。メッセージを変えたら、ここも合わせる
    private static final String INVALID_REQUEST = "入力内容に誤りがあります。";
    private static final String NOT_FOUND = "見つかりませんでした。";
    private static final String CONCURRENT_MODIFICATION = "他の人が先に更新しました。再読み込みしてください。";
    private static final String METHOD_NOT_ALLOWED = "この操作は許可されていません。";
    private static final String SYSTEM_ERROR = "予期しないエラーが発生しました。";

    /**
     * 日本語（漢字・ひらがな・カタカナ）を含む
     */
    private static final String JAPANESE = "(?s).*[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}].*";

    @Autowired
    MockMvc mockMvc;

    @Nested
    @DisplayName("アプリが投げる例外")
    class ApplicationExceptions {

        @Test
        @DisplayName("業務ルール違反は 400。メッセージのパラメータが埋め込まれる")
        void businessRuleViolation() throws Exception {
            mockMvc.perform(get(BASE + "/business-rule"))
                    .andExpect(status().isBadRequest())
                    .andExpectAll(problem("TEST_BUSINESS_RULE", "テスト用の業務ルールに違反しました（標準）。"));
        }

        @Test
        @DisplayName("見つからない場合は 404")
        void notFound() throws Exception {
            mockMvc.perform(get(BASE + "/not-found"))
                    .andExpect(status().isNotFound())
                    .andExpectAll(problem("NOT_FOUND", NOT_FOUND));
        }

        @Test
        @DisplayName("バージョン不一致は 409（CONCURRENT_MODIFICATION）")
        void concurrentModification() throws Exception {
            mockMvc.perform(get(BASE + "/conflict"))
                    .andExpect(status().isConflict())
                    .andExpectAll(problem("CONCURRENT_MODIFICATION", CONCURRENT_MODIFICATION));
        }

        @Test
        @DisplayName("システム例外は 500")
        void systemException() throws Exception {
            mockMvc.perform(get(BASE + "/system"))
                    .andExpect(status().isInternalServerError())
                    .andExpectAll(problem("SYSTEM_ERROR", SYSTEM_ERROR));
        }

        @Test
        @DisplayName("想定外の例外は 500")
        void unexpectedException() throws Exception {
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
            mockMvc.perform(post(BASE + "/validation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": " ", "size": 101}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpectAll(problem("INVALID_REQUEST", INVALID_REQUEST))
                    .andExpect(jsonPath("$.errors", hasSize(2)))
                    // フィルター式の結果は配列になる
                    .andExpect(jsonPath("$.errors[?(@.field == 'name')].message").value(contains(matchesPattern(JAPANESE))))
                    .andExpect(jsonPath("$.errors[?(@.field == 'size')].message").value(contains(matchesPattern(JAPANESE))));
        }

        @Test
        @DisplayName("メソッド引数の検証エラー（size の上限超え）は 400。errors が入る")
        void methodParameterValidation() throws Exception {
            mockMvc.perform(get(BASE + "/method-validation").param("size", "101"))
                    .andExpect(status().isBadRequest())
                    .andExpectAll(problem("INVALID_REQUEST", INVALID_REQUEST))
                    .andExpect(jsonPath("$.errors[0].field").value("size"))
                    .andExpect(jsonPath("$.errors[0].message").value(matchesPattern(JAPANESE)));
        }

        @Test
        @DisplayName("@Validated の Controller（生成インターフェース）でのメソッド引数の検証エラーも 400。errors が入る")
        void methodParameterValidationOnValidatedController() throws Exception {
            mockMvc.perform(get(VALIDATED_BASE + "/method-validation").param("size", "101"))
                    .andExpect(status().isBadRequest())
                    .andExpectAll(problem("INVALID_REQUEST", INVALID_REQUEST))
                    .andExpect(jsonPath("$.errors[0].field").value("size"))
                    .andExpect(jsonPath("$.errors[0].message").value(matchesPattern(JAPANESE)));
        }
    }

    @Nested
    @DisplayName("Spring MVC が投げる標準の例外")
    class FrameworkExceptions {

        @Test
        @DisplayName("パスの UUID の形式誤りは 400")
        void malformedUuidInPath() throws Exception {
            mockMvc.perform(get(BASE + "/items/not-a-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpectAll(problem("INVALID_REQUEST", INVALID_REQUEST));
        }

        @Test
        @DisplayName("必須パラメータの欠落は 400")
        void missingRequiredParameter() throws Exception {
            mockMvc.perform(get(BASE + "/required-param"))
                    .andExpect(status().isBadRequest())
                    .andExpectAll(problem("INVALID_REQUEST", INVALID_REQUEST));
        }

        @Test
        @DisplayName("パラメータの型の不一致は 400")
        void typeMismatch() throws Exception {
            mockMvc.perform(get(BASE + "/method-validation").param("size", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpectAll(problem("INVALID_REQUEST", INVALID_REQUEST));
        }

        @Test
        @DisplayName("JSON の構文誤りは 400")
        void malformedJson() throws Exception {
            mockMvc.perform(post(BASE + "/validation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": "))
                    .andExpect(status().isBadRequest())
                    .andExpectAll(problem("INVALID_REQUEST", INVALID_REQUEST));
        }

        @Test
        @DisplayName("存在しないパスは 404")
        void unknownPath() throws Exception {
            mockMvc.perform(get("/api/does-not-exist"))
                    .andExpect(status().isNotFound())
                    .andExpectAll(problem("NOT_FOUND", NOT_FOUND));
        }

        @Test
        @DisplayName("許可されていないメソッドは 405")
        void methodNotAllowed() throws Exception {
            mockMvc.perform(delete(BASE + "/not-found"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpectAll(problem("METHOD_NOT_ALLOWED", METHOD_NOT_ALLOWED));
        }
    }

    @Test
    @DisplayName("トレースの span がない状態でも traceId を返す（テストではトレーシングが無効）")
    void returnsTraceIdWithoutSpan() throws Exception {
        ResultActions result = mockMvc.perform(get(BASE + "/not-found"));

        result.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{32}")));
    }

    // ============================== 検証の部品 ==============================

    /**
     * すべてのエラーレスポンスに共通する形。
     *
     * @param errorCode エラーコード
     * @param message   画面にそのまま出せるメッセージ
     * @return 検証
     */
    private static ResultMatcher[] problem(String errorCode, String message) {
        return List.of(
                content().contentType(MediaType.APPLICATION_PROBLEM_JSON),
                jsonPath("$.errorCode").value(errorCode),
                jsonPath("$.message").value(message),
                jsonPath("$.traceId").isNotEmpty()
        ).toArray(ResultMatcher[]::new);
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
    }

    /**
     * @Validated の Controller（生成インターフェースと同じく、メソッド引数の検証が AOP で行われる）
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
