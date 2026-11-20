package com.ecsite.config.security;

import com.ecsite.support.config.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 起動時に mock のフラグを警告する Bean が登録されていることの検査
 *
 * <p>警告の出し方は単体テスト（{@code MockFlagWarningTest}）が確認する。ここでは、アプリの起動で警告が動くことを確認する。
 */
@IntegrationTest
class MockFlagWarningRegistrationTest {

    // ============================== Bean ==============================

    @Autowired
    ApplicationContext context;

    // ============================== テスト ==============================

    @Test
    @DisplayName("DC-477-4 アプリの起動で、mock のフラグの警告が動く")
    void warningIsRegistered() {
        // 確認：警告の Bean が 1 つある
        assertThat(context.getBeansOfType(MockFlagWarning.class)).hasSize(1);
    }
}
