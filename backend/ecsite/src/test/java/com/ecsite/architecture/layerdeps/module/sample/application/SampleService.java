package com.ecsite.architecture.layerdeps.module.sample.application;

import com.ecsite.architecture.layerdeps.module.sample.domain.SampleDomain;

/**
 * 層の依存の検査の例：application の直下に置いた service。<br />
 * 構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public class SampleService {

    /**
     * service が使う domain のクラス
     */
    SampleDomain domain;
}
