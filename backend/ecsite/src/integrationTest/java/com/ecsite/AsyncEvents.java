package com.ecsite;

import java.time.Duration;

/**
 * 非同期の処理（コミットの後に動くイベントのリスナー）を待つ補助。
 */
public final class AsyncEvents {

    /**
     * 非同期の処理を待つ時間の上限。長く待つ必要があるテストだけ、理由を書いて別の値を使う
     */
    public static final Duration TIMEOUT = Duration.ofSeconds(10);

    private AsyncEvents() {
    }
}
