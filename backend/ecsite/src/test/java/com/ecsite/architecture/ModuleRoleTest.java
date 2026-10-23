package com.ecsite.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.jmolecules.ddd.annotation.AggregateRoot;
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
     * レベル A のモジュール（docs/design/modules の各設計書の「レベル」と合わせる）
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

    @Test
    @DisplayName("レベル A のモジュールの presentation は、集約に依存しない（参照の service の view を受け取る）")
    void levelAPresentationDoesNotDependOnAggregates() {
        var classes = importModules();
        // presentation のないモジュール（payment）は、対象が 0 件になるので繰り返さない
        for (String module : modulesWithPresentation(classes)) {
            ArchRule rule = noClasses().that().resideInAPackage("com.ecsite.module." + module + ".presentation..")
                    .should().dependOnClassesThat().areAnnotatedWith(AggregateRoot.class);
            rule.check(classes);
        }
    }

    @Test
    @DisplayName("presentation は、生成したモデルを引数なしのコンストラクターと項目名のメソッドで組み立てる（位置で並べるコンストラクターを呼ばない）")
    void presentationBuildsGeneratedModelsWithFluentSetters() {
        var positional = DescribedPredicate.<JavaConstructorCall>describe("生成したモデルの引数を取るコンストラクター",
                call -> call.getTargetOwner().getPackageName().startsWith("com.ecsite.api")
                        && !call.getTarget().getRawParameterTypes().isEmpty());
        ArchRule rule = noClasses().that().resideInAPackage("com.ecsite.module..presentation..")
                .should().callConstructorWhere(positional);
        rule.check(importModules());
    }

    /**
     * presentation に本番のクラスがある、レベル A のモジュールの名前を返す。
     *
     * @param classes モジュールの本番のクラス
     * @return モジュールの名前
     */
    private static List<String> modulesWithPresentation(JavaClasses classes) {
        return LEVEL_A_MODULES.stream()
                .filter(module -> classes.stream()
                        .anyMatch(javaClass -> javaClass.getPackageName()
                                .startsWith("com.ecsite.module." + module + ".presentation")))
                .toList();
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
