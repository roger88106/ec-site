package com.ecsite.architecture.modelconstructor.sample.presentation;

import com.ecsite.architecture.modelconstructor.sample.api.SampleModel;

/**
 * 生成したモデルの組み立て方の検査の例：presentation 層に見立てたクラスから、引数なしのコンストラクターと項目名のメソッドで組み立てる（規則に合う）
 *
 * <p>構成の検査が、規則に合う組み立て方を違反にしないことを確認するための例であり、本番のコードではない。
 */
public class PresentationUsesFluent {

    /**
     * 引数なしのコンストラクターと項目名のメソッドで組み立てる。
     *
     * @return モデル
     */
    SampleModel build() {
        return new SampleModel().name("名前");
    }
}
