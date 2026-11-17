package com.ecsite.architecture.sample.naming;

/**
 * 名前の形の検査の例のうち、public でない例のクラス
 *
 * <p>public でない例を、ほかのパッケージのテストから参照するために置く。本番のコードではない。
 */
public final class NamingExamples {

    /**
     * 腐敗防止層の実装の名前が ApiDirectory の形でない例（public でない）
     */
    public static final Class<?> MISNAMED_DIRECTORY_ADAPTER = MisnamedExampleDirectoryAdapter.class;

    /**
     * インスタンスを作らない。
     */
    private NamingExamples() {
    }
}
