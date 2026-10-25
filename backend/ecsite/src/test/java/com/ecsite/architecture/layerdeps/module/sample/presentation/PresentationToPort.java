package com.ecsite.architecture.layerdeps.module.sample.presentation;

import com.ecsite.architecture.layerdeps.module.sample.application.port.SamplePort;

/**
 * 層の依存の検査の例：presentation から application の port を使う（表にない組なので規則に合わない）。<br />
 * 構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public class PresentationToPort {

    /**
     * presentation が使う port
     */
    SamplePort port;
}
