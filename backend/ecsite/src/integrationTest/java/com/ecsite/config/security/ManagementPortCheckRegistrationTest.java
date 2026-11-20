package com.ecsite.config.security;

import com.ecsite.support.config.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 起動時に運用ポートを検査する Bean が登録されていることの検査
 *
 * <p>判定は単体テスト（{@code ManagementPortCheckTest}）が確認する。ここでは、アプリの起動で検査が動くことを確認する。
 */
@IntegrationTest
class ManagementPortCheckRegistrationTest {

    // ============================== Bean ==============================

    @Autowired
    ApplicationContext context;

    @Autowired
    Environment environment;

    // ============================== テスト ==============================

    @Test
    @DisplayName("DC-477-1 アプリの起動で運用ポートの検査が動き、運用のパスを業務ポートで公開する統合テストの設定は、許す設定で起動する")
    void checkIsRegistered() {
        // 確認（前提）：統合テストの設定は、運用のパスが業務ポートで出る
        assertThat(ManagementPortType.get(environment)).isEqualTo(ManagementPortType.SAME);

        // 確認：検査の Bean があり、業務ポートで公開することを許している
        assertThat(context.getBeansOfType(ManagementPortCheck.class)).hasSize(1);
        assertThat(context.getBean(SecurityProperties.class).allowManagementOnBusinessPort()).isTrue();
    }
}
