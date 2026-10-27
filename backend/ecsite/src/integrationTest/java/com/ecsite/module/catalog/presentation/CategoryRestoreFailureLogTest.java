package com.ecsite.module.catalog.presentation;

import com.ecsite.CapturedLogs;
import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.common.model.base.AggregateGuards;
import com.ecsite.module.catalog.domain.category.Category;
import com.ecsite.module.catalog.domain.category.CategoryFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 集約の復元に失敗した要求のログのテスト。<br />
 * カテゴリのファクトリーを包み、復元の処理を、値を入れたメッセージの例外を投げるものに替える（集約を戻す共通の補助はそのまま通す）。
 * DB の値を直接壊す方法は、カテゴリの表の制約が集約の検証と同じ範囲を守るので使えず、メッセージに任意の値を入れることもできない。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class CategoryRestoreFailureLogTest {

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

    @Test
    @DisplayName("復元に失敗した要求は 500 の共通のシステムエラーを返し、ログには集約の名前と原因の例外の種類が出て、原因の例外のメッセージの値は出ない")
    void logsWithoutCauseMessages(CapturedOutput output) throws Exception {
        var id = new CatalogTestData(mockMvc, clock).category("衣類", null);
        var secret = "secret-" + UUID.randomUUID();
        doAnswer(invocation -> AggregateGuards.<Category>restore("category", () -> {
            throw failureWithChain(secret);
        })).when(categoryFactory).restore(any());

        var logStart = CapturedLogs.mark(output);
        mockMvc.perform(put("/api/admin/categories/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "ファッション", "version": 0}"""))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("SYSTEM_ERROR"));

        var logs = CapturedLogs.since(output, logStart);
        assertThat(logs)
                .contains("category restore error: " + IllegalArgumentException.class.getName())
                .contains(IllegalStateException.class.getName())
                .contains(UnsupportedOperationException.class.getName())
                .doesNotContain(secret);
    }

    /**
     * 値を入れたメッセージを持つ例外を作る。原因の原因と、抑制した例外を含む。
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
