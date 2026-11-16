package com.ecsite.architecture.sample.configprops;

/**
 * 設定の値の型と設定のクラスの検査の例：コントローラーが設定の値の型を受け取る（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public class PresentationExampleController {

    /**
     * 例の設定
     */
    private final ExampleProperties properties;

    PresentationExampleController(ExampleProperties properties) {
        this.properties = properties;
    }

    String value() {
        return properties.value();
    }
}
