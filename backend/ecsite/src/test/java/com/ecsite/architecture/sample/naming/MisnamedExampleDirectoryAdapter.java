package com.ecsite.architecture.sample.naming;

/**
 * 名前の形の検査の例：腐敗防止層の実装の名前が ApiDirectory の形でない（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 * public の規則に当たらない例にも使うので、public にしない。テストは {@link NamingExamples} から参照する。
 */
final class MisnamedExampleDirectoryAdapter implements ExampleDirectory {
}
