package com.ecsite.architecture.sample.idgeneration;

import java.security.SecureRandom;

/**
 * 識別子と乱数の生成の検査の例：乱数の Bean の設定に見立てたクラスが new SecureRandom() を書く（例外の一覧に入れると規則に合う）
 *
 * <p>構成の検査が例外の一覧のクラスを違反にしないことを確認するための例であり、本番のコードではない。
 */
public final class ExampleRandomConfig {

    /**
     * 例の関数。
     *
     * @return 乱数
     */
    SecureRandom create() {
        return new SecureRandom();
    }
}
