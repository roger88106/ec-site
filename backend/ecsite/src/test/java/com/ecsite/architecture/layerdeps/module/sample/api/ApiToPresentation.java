package com.ecsite.architecture.layerdeps.module.sample.api;

import com.ecsite.architecture.layerdeps.module.sample.presentation.SamplePresentation;

/**
 * 層の依存の検査の例：api から presentation を使う（表にない組なので規則に合わない）。<br />
 * 構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public class ApiToPresentation {

    /**
     * api が使う presentation のクラス
     */
    SamplePresentation presentation;
}
