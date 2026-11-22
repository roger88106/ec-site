package com.ecsite.support.persistence;

import com.ecsite.support.config.IntegrationTest;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 実行計画の補助の統合テスト
 *
 * <p>計画を強制する設定が、トランザクションの終わりに元に戻ることを確認する。
 * プールから別の接続を取って読むと確認にならないので、1 つの接続でトランザクションを始めて終える。
 */
@IntegrationTest
class QueryPlansTest {

    // ============================== Bean ==============================

    @Autowired
    DSLContext dsl;

    // ============================== テスト ==============================

    @Test
    @DisplayName("副問い合わせを再評価させる設定は、トランザクションの中だけで有効で、コミットの後は元に戻る")
    void rescanSettingsDoNotLeakOutsideTransaction() {
        dsl.connection(connection -> {
            // 準備：自動コミットを切って、トランザクションを始める
            var autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                // 準備：強制する前の設定の値
                var before = new HashMap<String, String>();
                for (var setting : QueryPlans.RESCAN_DISABLED_SETTINGS) {
                    before.put(setting, setting(connection, setting));
                }

                // 操作：計画を強制する
                var transaction = DSL.using(connection, dsl.dialect());
                QueryPlans.forceSubqueryRescan(transaction);

                // 確認：トランザクションの中では、全ての設定が無効
                for (var setting : QueryPlans.RESCAN_DISABLED_SETTINGS) {
                    assertThat(setting(connection, setting)).as(setting).isEqualTo("off");
                }

                // 操作：コミットする
                connection.commit();

                // 確認：同じ接続で、全ての設定が強制する前の値に戻る
                for (var setting : QueryPlans.RESCAN_DISABLED_SETTINGS) {
                    assertThat(setting(connection, setting)).as(setting).isEqualTo(before.get(setting));
                }
            } finally {
                connection.rollback();
                connection.setAutoCommit(autoCommit);
            }
        });
    }

    // ============================== 準備の補助 ==============================

    /**
     * 接続の設定の今の値を読む。
     *
     * @param connection 接続
     * @param name       設定の名前
     * @return 設定の値
     * @throws SQLException 読めなかったとき
     */
    private static String setting(Connection connection, String name) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT current_setting(?)")) {
            statement.setString(1, name);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }
}
