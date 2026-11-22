package com.ecsite.architecture.sample.idgeneration;

import java.util.UUID;

/**
 * 識別子と乱数の生成の検査の例：識別子の生成の実装に見立てたクラスが UUID.randomUUID() を呼ぶ（例外の一覧に入れると規則に合う）
 *
 * <p>構成の検査が例外の一覧のクラスを違反にしないことを確認するための例であり、本番のコードではない。
 */
public final class ExampleIdGenerator {

    /**
     * 例の関数。
     *
     * @return 識別子
     */
    UUID create() {
        return UUID.randomUUID();
    }
}
