package com.ecsite;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * エラーレスポンスの確認の補助
 *
 * <p>エラーレスポンス（Problem Details）の形を検証する。
 */
public final class ProblemAssertions {

    /**
     * インスタンスを作らない。
     */
    private ProblemAssertions() {
    }

    /**
     * Problem Details の形（API の規約）。
     *
     * @param errorCode エラーコード
     * @param message   画面にそのまま出せるメッセージ
     * @return 検証
     */
    public static ResultMatcher[] problem(String errorCode, String message) {
        return List.of(
                content().contentType(MediaType.APPLICATION_PROBLEM_JSON),
                jsonPath("$.errorCode").value(errorCode),
                jsonPath("$.message").value(message),
                jsonPath("$.traceId").isNotEmpty()
        ).toArray(ResultMatcher[]::new);
    }
}
