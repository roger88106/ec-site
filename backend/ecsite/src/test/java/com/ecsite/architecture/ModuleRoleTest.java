package com.ecsite.architecture;

import com.ecsite.ProductionClasses;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.lang.ArchRule;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 層の中の役割の検査（バックエンドの書き方 第 1.1 節、バックエンドのアーキテクチャ 第 3 節）。<br />
 * モジュールの構成を見る {@code ModuleStructureTest} とは検査の対象が違い、変更の理由も別なので、別のファイルに置く。
 */
class ModuleRoleTest {

    @Test
    @DisplayName("レベル A のモジュールの application は、イベントを送り出さない（集約が記録し、リポジトリが送り出す）")
    void levelAApplicationDoesNotPublishEvents() {
        var classes = ProductionClasses.modules();
        for (String module : ProductionClasses.LEVEL_A_MODULES) {
            ArchRule rule = noClasses().that().resideInAPackage(ProductionClasses.MODULE_PACKAGE_PREFIX + module + ".application..")
                    .should().dependOnClassesThat().areAssignableTo(ApplicationEventPublisher.class);
            rule.check(classes);
        }
    }

    @Test
    @DisplayName("レベル A のモジュールの presentation は、集約に依存しない（参照の service の view を受け取る）")
    void levelAPresentationDoesNotDependOnAggregates() {
        var classes = ProductionClasses.modules();
        // presentation のないモジュール（payment）は、対象が 0 件になるので繰り返さない
        List<String> modules = levelAModulesWithPresentation();
        ProductionClasses.requireNotEmpty(modules, "presentation を持つレベル A のモジュール");
        for (String module : modules) {
            ArchRule rule = noClasses().that().resideInAPackage(ProductionClasses.MODULE_PACKAGE_PREFIX + module + ".presentation..")
                    .should().dependOnClassesThat().areAnnotatedWith(AggregateRoot.class);
            rule.check(classes);
        }
    }

    @Test
    @DisplayName("presentation は、生成したモデルを引数なしのコンストラクターと項目名のメソッドで組み立てる（位置で並べるコンストラクターを呼ばない）")
    void presentationBuildsGeneratedModelsWithFluentSetters() {
        var positional = DescribedPredicate.<JavaConstructorCall>describe("生成したモデルの引数を取るコンストラクター",
                call -> call.getTargetOwner().getPackageName().startsWith(ProductionClasses.API_PACKAGE)
                        && !call.getTarget().getRawParameterTypes().isEmpty());
        ArchRule rule = noClasses().that().resideInAPackage(ProductionClasses.MODULE_PACKAGE + "..presentation..")
                .should().callConstructorWhere(positional);
        rule.check(ProductionClasses.modules());
    }

    /**
     * presentation に本番のクラスがある、レベル A のモジュールの名前を返す。
     *
     * @return モジュールの名前（名前の順）
     */
    private static List<String> levelAModulesWithPresentation() {
        Set<String> withPresentation = ProductionClasses.modulesWith("presentation");
        return ProductionClasses.LEVEL_A_MODULES.stream()
                .filter(withPresentation::contains)
                .toList();
    }
}
