package com.ecsite.architecture;

import com.ecsite.ProductionClasses;
import com.ecsite.architecture.sample.commondeps.sample.common.CommonToConfig;
import com.ecsite.architecture.sample.commondeps.sample.config.SampleConfig;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.ecsite.architecture.ArchRuleViolations.assertViolation;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 共通コード、設定、モジュールの間の依存の検査
 *
 * <p>根拠はバックエンドの書き方の「層とパッケージ」、バックエンドのアーキテクチャの「層とパッケージ」。
 * 共通コード（{@code common}）と設定（{@code config}）とモジュールの間の依存の向きを、本番のクラスファイルから確認する。
 */
class CommonDependencyTest {

    // ============================== テスト ==============================

    @Test
    @DisplayName("共通コードと設定は、モジュールに依存しない")
    void commonAndConfigDependOnNoModule() {
        // 操作と確認：本番のクラスに規則を当てる
        noClasses().that().resideInAnyPackage("com.ecsite.common..", "com.ecsite.config..")
                .should().dependOnClassesThat().resideInAPackage(ProductionClasses.MODULE_PACKAGE + "..")
                .check(ProductionClasses.all());
    }

    @Test
    @DisplayName("共通コードは、設定に依存しない")
    void commonDependsOnNoConfig() {
        // 操作と確認：本番のクラスに規則を当てる
        dependsOnNoPackage("com.ecsite.common..", "com.ecsite.config..").check(ProductionClasses.all());
    }

    @Test
    @DisplayName("共通コードが設定に依存しない規則は、設定に依存するクラスに当たる")
    void commonDependencyRuleMatchesClassDependingOnConfig() {
        // 準備：設定に依存する共通コードの例
        JavaClasses examples = new ClassFileImporter().importClasses(CommonToConfig.class, SampleConfig.class);

        // 操作：例のパッケージに置き換えた規則を当てる
        List<String> details = dependsOnNoPackage("com.ecsite.architecture.sample.commondeps.sample.common..",
                "com.ecsite.architecture.sample.commondeps.sample.config..")
                .evaluate(examples).getFailureReport().getDetails();

        // 確認：例の依存が違反になる
        assertViolation(details, CommonToConfig.class, SampleConfig.class);
    }

    @Test
    @DisplayName("モジュールは、設定に依存しない")
    void modulesDependOnNoConfig() {
        // 操作と確認：本番のクラスに規則を当てる
        noClasses().that().resideInAPackage(ProductionClasses.MODULE_PACKAGE + "..")
                .should().dependOnClassesThat().resideInAPackage("com.ecsite.config..")
                .check(ProductionClasses.all());
    }

    // ============================== 規則の補助 ==============================

    /**
     * あるパッケージが、別のパッケージに依存しない規則を作る。
     *
     * @param sourcePackage 依存する側のパッケージ
     * @param targetPackage 依存されない側のパッケージ
     * @return 規則
     */
    private static ArchRule dependsOnNoPackage(String sourcePackage, String targetPackage) {
        return noClasses().that().resideInAPackage(sourcePackage)
                .should().dependOnClassesThat().resideInAPackage(targetPackage);
    }
}
