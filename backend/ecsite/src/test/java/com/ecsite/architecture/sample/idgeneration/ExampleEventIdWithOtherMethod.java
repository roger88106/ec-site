package com.ecsite.architecture.sample.idgeneration;

import java.util.UUID;

/**
 * 識別子と乱数の生成の検査の例：イベント ID を作る静的メソッドのほかに、別のメソッドも UUID.randomUUID() を呼ぶ（別のメソッドは規則に合わない）
 *
 * <p>構成の検査が、例外のメソッドだけを除くことを確認するための例であり、本番のコードではない。
 */
public final class ExampleEventIdWithOtherMethod {

    /**
     * 例の関数。
     *
     * @return イベント ID
     */
    static UUID newEventId() {
        return UUID.randomUUID();
    }

    /**
     * 例の関数。例外のメソッドではない。
     *
     * @return 識別子
     */
    UUID other() {
        return UUID.randomUUID();
    }
}
