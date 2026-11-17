package com.ecsite.architecture.sample.configprops;

import org.springframework.context.annotation.Configuration;

/**
 * 設定の値の型と設定のクラスの検査の例：設定のクラスが infrastructure にない（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
@Configuration(proxyBeanMethods = false)
public class MisplacedExampleConfig {
}
