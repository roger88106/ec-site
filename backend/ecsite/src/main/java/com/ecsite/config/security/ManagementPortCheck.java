package com.ecsite.config.security;

import org.springframework.core.env.Environment;

/**
 * 運用ポートの起動時の検査
 *
 * <p>運用のパス（死活監視とメトリクス）が業務ポートで出る設定なら、起動に失敗させる。
 */
final class ManagementPortCheck {

    /**
     * 運用ポートの設定を検査する。
     *
     * @param environment 設定
     * @param properties  認証の設定
     */
    ManagementPortCheck(Environment environment, SecurityProperties properties) {
        // 判定は、テストの後に実装する
    }
}
