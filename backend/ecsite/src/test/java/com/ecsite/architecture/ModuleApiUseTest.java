package com.ecsite.architecture;

import com.ecsite.ProductionClasses;
import com.ecsite.architecture.sample.apiuse.module.sample.api.AllowedTargetApi;
import com.ecsite.architecture.sample.apiuse.module.sample.api.TargetApi;
import com.ecsite.architecture.sample.apiuse.module.user.application.ApplicationUsesAllowedApi;
import com.ecsite.architecture.sample.apiuse.module.user.application.ApplicationUsesTargetApi;
import com.ecsite.architecture.sample.apiuse.module.user.presentation.PresentationUsesAllowedApi;
import com.ecsite.module.member.api.CurrentMemberIds;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.PackageMatcher;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.ecsite.architecture.ArchRuleViolations.assertNoViolation;
import static com.ecsite.architecture.ArchRuleViolations.assertViolation;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 他のモジュールの api の使い方の検査
 *
 * <p>根拠はバックエンドの書き方の「他のモジュールは腐敗防止層で包む」。
 * 他のモジュールの api を使うのは、腐敗防止層（{@code infrastructure.directory}）だけであることを、本番のクラスファイルから確認する。
 * モジュールの間の依存の宣言は {@code ModulithTest} が確認する。
 */
class ModuleApiUseTest {

    // ============================== 定数 ==============================

    /**
     * 他のモジュールの api を、infrastructure.directory の外から使ってよい組
     *
     * <p>組を追加するときは、理由をコメントに書き、バックエンドの書き方の「他のモジュールは腐敗防止層で包む」の例外にも書く。
     */
    private static final List<AllowedApiUse> ALLOWED_API_USES = List.of(
            // 会員の ID は token から読むだけで、presentation が application へ引数で渡す。
            // 会員の窓口（port と実装）をモジュールごとに作ると、読み取りだけのために 3 組が増える
            new AllowedApiUse(CurrentMemberIds.class.getName(), ProductionClasses.MODULE_PACKAGE + ".*.presentation.."));

    /**
     * 違反の例のモジュールを置くパッケージ
     */
    private static final String EXAMPLE_MODULE_PACKAGE = "com.ecsite.architecture.sample.apiuse.module";

    /**
     * 違反の例で、api を使ってよい組の一覧
     *
     * <p>{@link AllowedTargetApi} を presentation から使うことだけを許す。
     */
    private static final List<AllowedApiUse> EXAMPLE_ALLOWED_API_USES = List.of(
            new AllowedApiUse(AllowedTargetApi.class.getName(), EXAMPLE_MODULE_PACKAGE + ".*.presentation.."));

    // ============================== テスト ==============================

    @Test
    @DisplayName("他のモジュールを包む部品の、部品ごとのパッケージ（*dir）がない")
    void noDirectoryPackages() {
        // 準備：本番のモジュールのクラスのパッケージ
        List<String> modulePackages = ProductionClasses.all().stream()
                .map(JavaClass::getPackageName)
                .filter(name -> name.startsWith(ProductionClasses.MODULE_PACKAGE))
                .toList();
        ProductionClasses.requireNotEmpty(modulePackages, "モジュールのクラスのパッケージ");

        // 操作：名前が dir で終わるパッケージを集める
        Set<String> dirPackages = modulePackages.stream()
                .filter(name -> name.endsWith("dir"))
                .collect(Collectors.toCollection(TreeSet::new));

        // 確認：ない
        assertThat(dirPackages).isEmpty();
    }

    @Nested
    @DisplayName("他のモジュールの api を使う場所")
    class ApiUsers {

        @Test
        @DisplayName("他のモジュールの api を使うのは infrastructure.directory だけである（許可の一覧を除く）")
        void otherModulesApiIsUsedOnlyByDirectories() {
            // 準備：api を持つモジュール
            Set<String> modulesWithApi = ProductionClasses.modulesWith("api");
            ProductionClasses.requireNotEmpty(modulesWithApi, "api を持つモジュール");

            // 操作と確認：モジュールごとに、本番のクラスに規則を当てる
            for (String module : modulesWithApi) {
                classes().that().resideOutsideOfPackages(
                                "%s.%s..".formatted(ProductionClasses.MODULE_PACKAGE, module),
                                ProductionClasses.MODULE_PACKAGE + ".*.infrastructure.directory..")
                        .should(new NotUseApiCondition(ProductionClasses.MODULE_PACKAGE, module, ALLOWED_API_USES))
                        .check(ProductionClasses.all());
            }
        }

        @Test
        @DisplayName("規則は、許可の一覧にない api の型を使う例に当たる")
        void ruleMatchesUnlistedApiType() {
            // 操作：例に規則を当てる
            List<String> details = exampleDetails();

            // 確認：一覧にない型を使う例が違反になる
            assertViolation(details, ApplicationUsesTargetApi.class, TargetApi.class);
        }

        @Test
        @DisplayName("規則は、許可の一覧の型を、一覧にない層から使う例に当たる")
        void ruleMatchesListedApiTypeFromUnlistedLayer() {
            // 操作：例に規則を当てる
            List<String> details = exampleDetails();

            // 確認：一覧の型を application から使う例が違反になる
            assertViolation(details, ApplicationUsesAllowedApi.class, AllowedTargetApi.class);
        }

        @Test
        @DisplayName("規則は、許可の一覧の使い方には当たらない")
        void ruleDoesNotMatchListedUse() {
            // 操作：例に規則を当てる
            List<String> details = exampleDetails();

            // 確認：一覧の型を presentation から使う例は違反にならない
            assertNoViolation(details, PresentationUsesAllowedApi.class);
        }

        @Test
        @DisplayName("api を使う許可の一覧は、全て本番のクラスで使われている")
        void everyAllowedApiUseIsUsed() {
            // 操作と確認：一覧の組ごとに、使う層の本番のクラスが api の型に依存している
            for (AllowedApiUse allowed : ALLOWED_API_USES) {
                boolean used = ProductionClasses.all().stream()
                        .filter(javaClass -> PackageMatcher.of(allowed.userPackage()).matches(javaClass.getPackageName()))
                        .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                        .anyMatch(dependency -> dependency.getTargetClass().getName().equals(allowed.apiType()));
                assertThat(used).as(allowed.apiType()).isTrue();
            }
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 違反の例に、例のモジュール（sample）の api の規則を当てた違反の詳細を返す。
     *
     * <p>例は、一覧にない型を application から使う例、一覧の型を application から使う例、一覧の型を presentation から使う例。
     *
     * @return 違反の詳細
     */
    private static List<String> exampleDetails() {
        JavaClasses examples = new ClassFileImporter().importClasses(ApplicationUsesTargetApi.class,
                ApplicationUsesAllowedApi.class, PresentationUsesAllowedApi.class);
        return classes().should(new NotUseApiCondition(EXAMPLE_MODULE_PACKAGE, "sample", EXAMPLE_ALLOWED_API_USES))
                .evaluate(examples).getFailureReport().getDetails();
    }

    // ============================== 規則の補助 ==============================

    /**
     * api の型の使い方が、許可の一覧にあるかを返す。
     *
     * @param user        使う側のクラス
     * @param target      使われる api の型
     * @param allowedUses api を使ってよい組の一覧
     * @return 許可の一覧にあれば true
     */
    private static boolean isAllowed(JavaClass user, JavaClass target, List<AllowedApiUse> allowedUses) {
        return allowedUses.stream().anyMatch(allowed ->
                allowed.apiType().equals(target.getName())
                        && PackageMatcher.of(allowed.userPackage()).matches(user.getPackageName()));
    }

    /**
     * 他のモジュールの api を、許可の一覧にない使い方で使わないという条件
     */
    private static final class NotUseApiCondition extends ArchCondition<JavaClass> {

        /**
         * api のパッケージの名前
         */
        private final String apiPackage;

        /**
         * api を使ってよい組の一覧
         */
        private final List<AllowedApiUse> allowedUses;

        /**
         * 条件を作る。
         *
         * @param modulePackage モジュールを置くパッケージ（本番は {@link ProductionClasses#MODULE_PACKAGE}。検査の例は例のパッケージ）
         * @param module        api を持つモジュールの名前
         * @param allowedUses   api を使ってよい組の一覧
         */
        NotUseApiCondition(String modulePackage, String module, List<AllowedApiUse> allowedUses) {
            super("%s の api を、許可の一覧にない使い方で使わない".formatted(module));
            this.apiPackage = "%s.%s.api".formatted(modulePackage, module);
            this.allowedUses = allowedUses;
        }

        /**
         * クラスの依存のうち、許可の一覧にない api への依存を違反にする。
         *
         * @param javaClass 依存元のクラス
         * @param events    違反の記録
         */
        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                if (ProductionClasses.isInPackage(target.getPackageName(), apiPackage) && !isAllowed(javaClass, target, allowedUses)) {
                    events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                }
            }
        }
    }

    /**
     * 他のモジュールの api を使ってよい組
     *
     * @param apiType     使われる api の型の完全修飾名
     * @param userPackage 使ってよい層のパッケージ（ArchUnit の指定）
     */
    private record AllowedApiUse(String apiType, String userPackage) {
    }
}
