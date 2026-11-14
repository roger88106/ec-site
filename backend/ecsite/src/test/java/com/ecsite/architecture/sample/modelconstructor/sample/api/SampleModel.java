package com.ecsite.architecture.sample.modelconstructor.sample.api;

/**
 * 生成したモデルの組み立て方の検査の例：生成したモデルに見立てたクラス
 *
 * <p>構成の検査が、位置で並べるコンストラクターの呼び出しを見分けることを確認するための例であり、本番のコードではない。
 */
public class SampleModel {

    /**
     * 名前
     */
    private String name;

    /**
     * 引数なしで作る。
     */
    public SampleModel() {
    }

    /**
     * 項目を位置で並べて作る。
     *
     * @param name 名前
     */
    public SampleModel(String name) {
        this.name = name;
    }

    /**
     * 名前を設定する。
     *
     * @param name 名前
     * @return このモデル
     */
    public SampleModel name(String name) {
        this.name = name;
        return this;
    }

    /**
     * 名前を返す。
     *
     * @return 名前
     */
    public String getName() {
        return name;
    }
}
