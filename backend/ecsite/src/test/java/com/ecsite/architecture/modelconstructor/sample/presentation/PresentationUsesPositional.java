package com.ecsite.architecture.modelconstructor.sample.presentation;

import com.ecsite.architecture.modelconstructor.sample.api.SampleModel;

/**
 * 生成したモデルの組み立て方の検査の例：presentation 層に見立てたクラスから、位置で並べるコンストラクターを呼ぶ（規則に反する）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public class PresentationUsesPositional {

    /**
     * 位置で並べるコンストラクターで組み立てる。
     *
     * @return モデル
     */
    SampleModel build() {
        return new SampleModel("名前");
    }
}
