package com.ecsite.architecture;

import com.ecsite.ProductionClasses;
import com.ecsite.architecture.sample.modelconstructor.sample.api.SampleModel;
import com.ecsite.architecture.sample.modelconstructor.sample.presentation.PresentationUsesFluent;
import com.ecsite.architecture.sample.modelconstructor.sample.presentation.PresentationUsesPositional;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 層の中の役割の検査
 *
 * <p>根拠はバックエンドの書き方の「標準の構成」、バックエンドのアーキテクチャの「モジュール間の連携」。
 * 層の依存の向きを見る {@code LayerDependencyTest} とは検査の対象が違い、変更の理由も別なので、別のファイルに置く。
 */
class ModuleRoleTest {

    // ============================== 定数 ==============================

    /**
     * 違反の例の、生成したモデルのパッケージ
     */
    private static final String EXAMPLE_API_PACKAGE = "com.ecsite.architecture.sample.modelconstructor.sample.api";

    /**
     * 違反の例の、presentation のパッケージ
     */
    private static final String EXAMPLE_PRESENTATION_PACKAGE = "com.ecsite.architecture.sample.modelconstructor.sample.presentation..";

    // ============================== テスト ==============================

    @Nested
    @DisplayName("レベル A のモジュール")
    class LevelAModules {

        @Test
        @DisplayName("application は、イベントを送り出さない（集約が記録し、リポジトリが送り出す）")
        void levelAApplicationDoesNotPublishEvents() {
            // 準備：本番のモジュールのクラス
            var classes = ProductionClasses.modules();

            // 操作と確認：レベル A のモジュールごとに、規則を当てる
            for (String module : ProductionClasses.LEVEL_A_MODULES) {
                ArchRule rule = noClasses().that().resideInAPackage(ProductionClasses.MODULE_PACKAGE_PREFIX + module + ".application..")
                        .should().dependOnClassesThat().areAssignableTo(ApplicationEventPublisher.class);
                rule.check(classes);
            }
        }

        @Test
        @DisplayName("presentation は、集約に依存しない（参照の service の view を受け取る）")
        void levelAPresentationDoesNotDependOnAggregates() {
            // 準備：本番のモジュールのクラスと、presentation を持つレベル A のモジュール
            // （presentation のないモジュール（payment）は、対象が 0 件になるので繰り返さない）
            var classes = ProductionClasses.modules();
            List<String> modules = levelAModulesWithPresentation();
            ProductionClasses.requireNotEmpty(modules, "presentation を持つレベル A のモジュール");

            // 操作と確認：モジュールごとに、規則を当てる
            for (String module : modules) {
                ArchRule rule = noClasses().that().resideInAPackage(ProductionClasses.MODULE_PACKAGE_PREFIX + module + ".presentation..")
                        .should().dependOnClassesThat().areAnnotatedWith(AggregateRoot.class);
                rule.check(classes);
            }
        }
    }

    @Nested
    @DisplayName("生成したモデルの組み立て方")
    class GeneratedModelConstruction {

        @Test
        @DisplayName("presentation は、生成したモデルを引数なしのコンストラクターと項目名のメソッドで組み立てる（位置で並べるコンストラクターを呼ばない）")
        void presentationBuildsGeneratedModelsWithFluentSetters() {
            // 操作と確認：本番のモジュールのクラスに規則を当てる
            positionalConstructorRule(ProductionClasses.API_PACKAGE, ProductionClasses.MODULE_PACKAGE + "..presentation..")
                    .check(ProductionClasses.modules());
        }

        @Test
        @DisplayName("規則は、位置で並べるコンストラクターの呼び出しに当たる")
        void ruleMatchesPositionalCall() {
            // 操作：2 通りの組み立て方の例に、規則を当てる
            List<String> details = exampleDetails();

            // 確認：位置で並べるコンストラクターを呼ぶ例が、1 件の違反になる
            assertThat(details).filteredOn(detail -> detail.contains(PresentationUsesPositional.class.getName())).hasSize(1);
        }

        @Test
        @DisplayName("規則は、引数なしのコンストラクターの呼び出しには当たらない")
        void ruleDoesNotMatchFluentCall() {
            // 操作：2 通りの組み立て方の例に、規則を当てる
            List<String> details = exampleDetails();

            // 確認：引数なしのコンストラクターと項目名のメソッドで組み立てる例は、違反にならない
            ArchRuleViolations.assertNoViolation(details, PresentationUsesFluent.class);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 生成したモデルの組み立て方の例に、例のパッケージに置き換えた規則を当てた違反の詳細を返す。
     *
     * @return 違反の詳細
     */
    private static List<String> exampleDetails() {
        JavaClasses examples = new ClassFileImporter().importClasses(
                PresentationUsesPositional.class, PresentationUsesFluent.class, SampleModel.class);
        return positionalConstructorRule(EXAMPLE_API_PACKAGE, EXAMPLE_PRESENTATION_PACKAGE)
                .evaluate(examples).getFailureReport().getDetails();
    }

    // ============================== 規則の補助 ==============================

    /**
     * presentation が、生成したモデルの引数を取るコンストラクターを呼ばない、という規則を返す。
     *
     * @param apiPackage          生成したモデルのパッケージ（その下を含む）
     * @param presentationPackage presentation のパッケージ（ArchUnit の指定）
     * @return 規則
     */
    private static ArchRule positionalConstructorRule(String apiPackage, String presentationPackage) {
        var positional = DescribedPredicate.<JavaConstructorCall>describe("生成したモデルの引数を取るコンストラクター",
                call -> call.getTargetOwner().getPackageName().startsWith(apiPackage)
                        && !call.getTarget().getRawParameterTypes().isEmpty());
        return noClasses().that().resideInAPackage(presentationPackage)
                .should().callConstructorWhere(positional);
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
