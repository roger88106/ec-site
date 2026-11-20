package com.ecsite.config.security;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.core.env.Environment;

/**
 * 運用ポートの起動時の検査
 *
 * <p>運用のパス（死活監視とメトリクス）が業務ポートで出る設定なら、起動に失敗させる。業務ポートでは、運用のパスを認証なしで許しているため。
 * 運用ポートを無効にした設定と、運用のパスを業務ポートで公開することを設定で許したときは、起動に失敗しない。
 */
@RequiredArgsConstructor
class ManagementPortCheck implements InitializingBean {

    /**
     * アプリの設定
     */
    @NonNull
    private final Environment environment;

    /**
     * 認証の設定
     */
    @NonNull
    private final SecurityProperties properties;

    /**
     * 運用ポートの設定を検査する。
     *
     * <p>業務ポートと同じかは、Spring Boot が運用ポートを分けるかを決めるのと同じ判定で決める（運用ポートを設定しないときも同じとする）。
     */
    @Override
    public void afterPropertiesSet() {
        if (ManagementPortType.get(environment) == ManagementPortType.SAME && !properties.allowManagementOnBusinessPort()) {
            throw new IllegalStateException("運用のパスが業務ポートで出る設定になっている。management.server.port を server.port と違うポートにするか、"
                    + "業務ポートで出してよい環境（統合テストなど）なら ecsite.security.allow-management-on-business-port を true にする");
        }
    }
}
