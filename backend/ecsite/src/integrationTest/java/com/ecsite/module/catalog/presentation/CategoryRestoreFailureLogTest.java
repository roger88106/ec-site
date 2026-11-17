package com.ecsite.module.catalog.presentation;

import com.ecsite.common.model.base.RestoreGuard;
import com.ecsite.module.catalog.domain.category.Category;
import com.ecsite.module.catalog.domain.category.CategoryFactory;
import com.ecsite.support.LogCapture;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 集約の復元に失敗したリクエストのログのテスト
 *
 * <p>カテゴリのファクトリーを包み、復元の処理を、値を入れたメッセージの例外を投げるものに替える（集約を戻す共通の補助はそのまま通す）。
 * DB の値を直接壊す方法は、カテゴリの表の制約が集約の検証と同じ範囲を守るので使えず、メッセージに任意の値を入れることもできない。
 *
 * <p>メッセージに入れる値は、ほかのログと混ざらないよう実行ごとに作る。
 */
@IntegrationTest
@Transactional
class CategoryRestoreFailureLogTest {

    /**
     * 例外の処理が出したログ
     *
     * <p>例外の処理のクラスはパッケージの外から見えないので、ロガーの名前で指定する。
     */
    @RegisterExtension
    final LogCapture handlerLogs = LogCapture.of("com.ecsite.common.error.handler.GlobalExceptionHandler");

    /**
     * 全てのロガーのログ（メッセージの値が、どのロガーにも出ないことを確認するため）
     */
    @RegisterExtension
    final LogCapture allLogs = LogCapture.all();

    // ============================== Bean ==============================

    /**
     * API を呼ぶ MockMvc
     */
    @Autowired
    MockMvc mockMvc;

    /**
     * テストの時刻
     */
    @Autowired
    MutableClock clock;

    /**
     * カテゴリのファクトリー（復元の処理を失敗するものに替えるため、本物を包む）
     */
    @MockitoSpyBean
    CategoryFactory categoryFactory;

    // ============================== テスト ==============================

    @Test
    @DisplayName("復元に失敗したリクエストは、500 の共通のシステムエラーを返す")
    void returnsSystemError() throws Exception {
        // 準備：カテゴリの復元が失敗するようにする
        var id = categoryFailingToRestore("secret-" + UUID.randomUUID());

        // 操作と確認：名称の変更は 500 の SYSTEM_ERROR
        rename(id)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("SYSTEM_ERROR"));
    }

    @Test
    @DisplayName("復元に失敗したリクエストのログには、集約の名前と、原因の例外の種類が出る")
    void logsAggregateAndCauseTypes() throws Exception {
        // 準備：カテゴリの復元が、原因の原因と抑制した例外を持つ例外で失敗するようにする
        var id = categoryFailingToRestore("secret-" + UUID.randomUUID());

        // 操作：名称を変える
        handlerLogs.clear();
        rename(id).andExpect(status().isInternalServerError());

        // 確認：エラーのログに、集約の名前と、投げた例外・原因・抑制した例外の種類が出る
        assertThat(errorLogText())
                .contains("category restore error: " + IllegalArgumentException.class.getName())
                .contains(IllegalStateException.class.getName())
                .contains(UnsupportedOperationException.class.getName());
    }

    @Test
    @DisplayName("復元に失敗したリクエストのログには、原因の例外のメッセージの値が出ない")
    void logsWithoutCauseMessages() throws Exception {
        // 準備：カテゴリの復元が、メッセージに値を入れた例外で失敗するようにする
        var secret = "secret-" + UUID.randomUUID();
        var id = categoryFailingToRestore(secret);

        // 操作：名称を変える
        allLogs.clear();
        rename(id).andExpect(status().isInternalServerError());

        // 確認：メッセージに入れた値が、どのログにも出ない
        assertThat(allLogs.text()).doesNotContain(secret);
    }

    // ============================== 確認の補助 ==============================

    /**
     * 例外の処理が出したエラーのログを、例外の出力を含めて 1 つの文字列にする。
     *
     * @return エラーのログを改行でつないだ文字列
     */
    private String errorLogText() {
        return String.join("\n", handlerLogs.entries().stream()
                .filter(entry -> entry.level() == Level.ERROR)
                .map(LogCapture.LogEntry::text)
                .toList());
    }

    // ============================== 準備の補助 ==============================

    /**
     * カテゴリを登録し、その後の復元が失敗するようにする。
     *
     * @param secret 復元で投げる例外のメッセージに入れる値（ログに出ないことを確認するので、実行ごとに作る）
     * @return カテゴリ ID
     */
    private UUID categoryFailingToRestore(String secret) throws Exception {
        var id = new CatalogTestData(mockMvc, clock).category("衣類", null);
        doAnswer(invocation -> RestoreGuard.<Category>restore("category", () -> {
            throw failureWithChain(secret);
        })).when(categoryFactory).restore(any());
        return id;
    }

    /**
     * カテゴリの名称を変える（変更の前にカテゴリを復元する）。
     *
     * @param id カテゴリ ID
     * @return 応答
     */
    private ResultActions rename(UUID id) throws Exception {
        return mockMvc.perform(put("/api/admin/categories/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "ファッション", "version": 0}"""));
    }

    /**
     * 値を入れたメッセージを持つ例外を作る。
     *
     * <p>原因の原因と、抑制した例外を含む。
     *
     * @param secret メッセージに入れる値
     * @return 復元で投げる例外
     */
    private static IllegalArgumentException failureWithChain(String secret) {
        var failure = new IllegalArgumentException("failure " + secret, new IllegalStateException("root " + secret));
        failure.addSuppressed(new UnsupportedOperationException("suppressed " + secret));
        return failure;
    }
}
