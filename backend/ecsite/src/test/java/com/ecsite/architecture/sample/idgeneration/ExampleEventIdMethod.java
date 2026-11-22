package com.ecsite.architecture.sample.idgeneration;

import java.util.UUID;

/**
 * 識別子と乱数の生成の検査の例：イベント ID を作る静的メソッドだけが UUID.randomUUID() を呼ぶ（メソッド名を例外にすると規則に合う）
 *
 * <p>構成の検査が、例外のメソッドを違反にしないことを確認するための例であり、本番のコードではない。
 */
public final class ExampleEventIdMethod {

    /**
     * 例の関数。
     *
     * @return イベント ID
     */
    static UUID newEventId() {
        return UUID.randomUUID();
    }
}
