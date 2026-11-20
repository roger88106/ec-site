package com.ecsite.config.security;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;

/**
 * mock のフラグの起動時の警告
 *
 * <p>決済と物流の mock のフラグを立てて起動すると、立てたモジュールごとに、本番では立てないことを警告のログに出力する。
 * mock の画面は認証なしで公開されるため。起動は止めない。
 */
@Slf4j
@RequiredArgsConstructor
class MockFlagWarning implements InitializingBean {

    /**
     * アプリの設定
     */
    @NonNull
    private final Environment environment;

    /**
     * mock のフラグを立てたモジュールごとに、警告のログを出力する。
     */
    @Override
    public void afterPropertiesSet() {
        for (var page : MockPage.enabled(environment)) {
            log.warn("{}の mock を使う（{}=true）。mock の画面を認証なしで公開するので、本番ではこのフラグを立てない",
                    page.getModuleName(), page.getFlag());
        }
    }
}
