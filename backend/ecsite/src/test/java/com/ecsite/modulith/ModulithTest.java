package com.ecsite.modulith;

import com.ecsite.ProductionClasses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * モジュールの境界の検証（バックエンドのアーキテクチャ 第 1 節）。<br />
 * {@code package-info.java} で宣言した依存（{@code allowedDependencies}）以外の依存、内部のパッケージへの参照、
 * モジュール間の循環を検出する。モジュールの依存を変えたら、先に設計の文書（docs/03-機能仕様）を更新する。
 */
class ModulithTest {

    /**
     * Spring Modulith のモジュールの一覧（テストのクラスを除く）
     */
    private final ApplicationModules modules = ProductionClasses.applicationModules();

    @Test
    @DisplayName("モジュール間の依存が宣言のとおりである")
    void verifiesModuleDependencies() {
        modules.verify();
    }

    @Test
    @DisplayName("税・商品カタログ・メディア・在庫・カート・注文・店舗・会員・決済・出荷・通知・レポート・地域の 13 モジュールがある")
    void detectsAllModules() {
        assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
                .containsExactlyInAnyOrder("tax", "catalog", "media", "inventory", "cart", "order", "storefront", "member", "payment", "shipping", "notification", "reporting", "region");
    }
}
