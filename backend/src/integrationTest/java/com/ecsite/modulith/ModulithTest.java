package com.ecsite.modulith;

import com.ecsite.EcSiteApplication;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * モジュールの境界の検証（T018、constitution 4.1）。<br />
 * {@code package-info.java} で宣言した依存（{@code allowedDependencies}）以外の依存、内部のパッケージへの参照、
 * モジュール間の循環を検出する。モジュールの依存を変えたら、先に plan と docs/modules を更新する。
 */
class ModulithTest {

    /**
     * テストのクラス（名前が Test で終わるクラスと、その入れ子のクラス）。<br />
     * テストは準備のために他のモジュールの内部（リポジトリなど）を直接使うことがあるので、境界の検証から除く。
     * 検証するのは本番のコードの境界。
     */
    private static final DescribedPredicate<JavaClass> TEST_CLASSES = DescribedPredicate.describe(
            "test classes", javaClass -> javaClass.getName().matches(".*Test(\\$.*)?"));

    private final ApplicationModules modules = ApplicationModules.of(EcSiteApplication.class, TEST_CLASSES);

    @Test
    @DisplayName("モジュール間の依存が宣言のとおりである")
    void verifiesModuleDependencies() {
        modules.forEach(System.out::println);
        modules.verify();
    }

    @Test
    @DisplayName("税・商品カタログ・メディア・在庫・店舗の 5 モジュールがある")
    void detectsAllModules() {
        assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
                .containsExactlyInAnyOrder("tax", "catalog", "media", "inventory", "storefront");
    }
}
