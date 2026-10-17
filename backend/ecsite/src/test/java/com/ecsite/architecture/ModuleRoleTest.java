package com.ecsite.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 層の中の役割の検査（バックエンドの書き方 第 1.1 節、バックエンドのアーキテクチャ 第 3 節、spec 013）。<br />
 * spec 013 は既存のテストを変えないので、{@code ModuleStructureTest} とは別のファイルに置く。
 */
class ModuleRoleTest {

    /**
     * レベル A のモジュール（docs/backend/modules の各設計書の「レベル」と合わせる）
     */
    private static final List<String> LEVEL_A_MODULES = List.of("tax", "catalog", "inventory", "order", "payment");

    @Test
    @DisplayName("レベル A のモジュールの application は、イベントを送り出さない（集約が記録し、リポジトリが送り出す）")
    void levelAApplicationDoesNotPublishEvents() {
        var classes = importModules();
        for (String module : LEVEL_A_MODULES) {
            ArchRule rule = noClasses().that().resideInAPackage("com.ecsite.module." + module + ".application..")
                    .should().dependOnClassesThat().areAssignableTo(ApplicationEventPublisher.class);
            rule.check(classes);
        }
    }

    /**
     * モジュールの本番のクラス（テストを除く）を読む。<br />
     * 他の ArchUnit のテストがクラスの全体を static に持つので、テストの JVM のメモリを抑えるため、
     * static に持たず、範囲もモジュールに絞る。
     *
     * @return モジュールの本番のクラス
     */
    private static JavaClasses importModules() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.ecsite.module");
    }
}
