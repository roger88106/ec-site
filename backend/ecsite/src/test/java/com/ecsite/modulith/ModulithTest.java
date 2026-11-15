package com.ecsite.modulith;

import com.ecsite.ProductionClasses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * モジュールの境界の検証
 *
 * <p>バックエンドのアーキテクチャの「モジュラーモノリス」に沿う。{@code package-info.java} で宣言した依存（{@code allowedDependencies}）以外の依存、内部のパッケージへの参照、
 * モジュール間の循環を検出する。モジュールの依存を変えたら、先に全体構成の「モジュールの依存とイベント」を更新する（{@code ModuleDiagramTest} が照合する）。
 */
class ModulithTest {

    // ============================== 定数 ==============================

    /**
     * 設計の 13 のモジュールの識別子
     */
    private static final List<String> DESIGNED_MODULES = List.of(
            "tax", "catalog", "media", "inventory", "cart", "order", "storefront", "member", "payment", "shipping",
            "notification", "reporting", "region");

    /**
     * Spring Modulith のモジュールの一覧（テストのクラスを除く）
     */
    private final ApplicationModules modules = ProductionClasses.applicationModules();

    // ============================== テスト ==============================

    @Test
    @DisplayName("モジュール間の依存が宣言のとおりである")
    void verifiesModuleDependencies() {
        // 操作と確認：宣言の外の依存、内部のパッケージへの参照、循環があれば失敗する
        modules.verify();
    }

    @Test
    @DisplayName("Spring Modulith が検出するモジュールは、設計の 13 のモジュールと一致する")
    void detectsAllModules() {
        // 操作と確認：検出したモジュールの識別子が、設計のモジュールと過不足なく一致する
        assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
                .containsExactlyInAnyOrderElementsOf(DESIGNED_MODULES);
    }
}
