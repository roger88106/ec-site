package com.ecsite.architecture.sample.staticonly;

/**
 * インスタンスを作らないクラスの検査の例：ほかのクラスを継ぐ（自分のメンバーはコンストラクタだけなので、対象でない）
 *
 * <p>構成の検査が規則に合う例を違反にしないことを確認するための例であり、本番のコードではない。
 */
public class ExampleException extends RuntimeException {

    /**
     * 例のコンストラクター。
     *
     * @param message 例の文
     */
    ExampleException(String message) {
        super(message);
    }
}
