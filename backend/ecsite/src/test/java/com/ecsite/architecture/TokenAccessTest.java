package com.ecsite.architecture;

import com.ecsite.ProductionClasses;
import com.ecsite.architecture.sample.tokenaccess.sample.application.ApplicationUsesCurrentMember;
import com.ecsite.architecture.sample.tokenaccess.sample.infrastructure.directory.DirectoryUsesCurrentMemberIds;
import com.ecsite.architecture.sample.tokenaccess.sample.presentation.PresentationUsesCurrentMember;
import com.ecsite.common.security.CurrentMember;
import com.ecsite.common.security.CurrentOperator;
import com.ecsite.common.security.CurrentToken;
import com.ecsite.module.member.api.CurrentMemberIds;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.ecsite.ProductionClasses.outermostName;
import static com.ecsite.architecture.ArchRuleViolations.assertNoViolation;
import static com.ecsite.architecture.ArchRuleViolations.assertViolation;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * token の取り出しを使う場所の検査
 *
 * <p>根拠はバックエンドの書き方の「標準の構成」。
 * token の取り出し（会員・運営者・会員 ID の取り出しを含む）を使う場所を、本番のクラスファイルから確認する。
 * 使ってよいのは、presentation 層と {@code common.security} と例外の一覧のクラスだけである。
 */
class TokenAccessTest {

    // ============================== 定数 ==============================

    /**
     * presentation の外に置く例外の {@code @RestController}
     *
     * <p>どのモジュールにも属さない GET /me。
     */
    private static final String ME_CONTROLLER = "com.ecsite.config.security.MeController";

    /**
     * token の取り出しのクラスの名前
     *
     * <p>token、会員、運営者、会員 ID の取り出しを指す。
     * 会員 ID の取り出し（CurrentMemberIds）を含める。他のモジュールの api の規則は infrastructure.directory からの利用を除くので、
     * それだけでは directory を経て application 層がリクエストの会員 ID を得る形を防げないため。
     */
    private static final Set<String> TOKEN_ACCESSORS = Set.of(
            CurrentToken.class.getName(), CurrentMember.class.getName(), CurrentOperator.class.getName(),
            CurrentMemberIds.class.getName());

    /**
     * token の取り出しを、presentation 層と common.security の外から使ってよいクラス
     *
     * <p>追加するときは、理由をコメントに書き、バックエンドの書き方の「標準の構成」の例外の表にも書く。
     */
    private static final Set<String> TOKEN_ACCESS_EXCEPTIONS = Set.of(
            // 他のモジュールの presentation に会員の ID を渡す公開の API。自身も取り出しの対象（TOKEN_ACCESSORS）で、呼び出し元は presentation に限る
            CurrentMemberIds.class.getName(),
            // モジュールに属さない API の入口で、役割は presentation 層と同じ
            ME_CONTROLLER);

    /**
     * 違反の例の presentation 層のパッケージ
     */
    private static final String EXAMPLE_PRESENTATION_PACKAGE = "com.ecsite.architecture.sample.tokenaccess.sample.presentation..";

    // ============================== テスト ==============================

    @Test
    @DisplayName("token の取り出しと会員・運営者の取り出しは、presentation、common.security、許可の一覧のクラスだけが使う")
    void tokenAccessorsAreUsedOnlyInAllowedPlaces() {
        // 操作と確認：本番のクラスに規則を当てる
        tokenAccessRule(ProductionClasses.MODULE_PACKAGE + ".*.presentation..").check(ProductionClasses.all());
    }

    @Test
    @DisplayName("規則は、application 層から会員の取り出しを使う例に当たる")
    void ruleMatchesApplicationExample() {
        // 操作：application 層と presentation 層から会員の取り出しを使う例に、規則を当てる
        List<String> details = exampleDetails(ApplicationUsesCurrentMember.class, PresentationUsesCurrentMember.class);

        // 確認：application 層の例が違反になる
        assertViolation(details, ApplicationUsesCurrentMember.class, CurrentMember.class);
    }

    @Test
    @DisplayName("規則は、presentation 層から会員の取り出しを使う例には当たらない")
    void ruleDoesNotMatchPresentationExample() {
        // 操作：application 層と presentation 層から会員の取り出しを使う例に、規則を当てる
        List<String> details = exampleDetails(ApplicationUsesCurrentMember.class, PresentationUsesCurrentMember.class);

        // 確認：presentation 層の例は違反にならない
        assertNoViolation(details, PresentationUsesCurrentMember.class);
    }

    @Test
    @DisplayName("規則は、infrastructure.directory から会員 ID の取り出しを使う例に当たる")
    void ruleMatchesDirectoryUsingCurrentMemberIds() {
        // 操作：腐敗防止層から会員 ID の取り出しを使う例に、規則を当てる
        List<String> details = exampleDetails(DirectoryUsesCurrentMemberIds.class);

        // 確認：例が違反になる
        assertViolation(details, DirectoryUsesCurrentMemberIds.class, CurrentMemberIds.class);
    }

    @Test
    @DisplayName("token の取り出しを使う許可の一覧は、全て本番のクラスで使われている")
    void everyTokenAccessExceptionIsUsed() {
        // 操作と確認：一覧のクラスごとに、本番のクラスが token の取り出しに依存している
        for (String exception : TOKEN_ACCESS_EXCEPTIONS) {
            boolean used = ProductionClasses.all().stream()
                    .filter(javaClass -> outermostName(javaClass).equals(exception))
                    .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                    .anyMatch(dependency -> TOKEN_ACCESSORS.contains(dependency.getTargetClass().getName()));
            assertThat(used).as(exception).isTrue();
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 違反の例に、例のパッケージに置き換えた規則を当てた違反の詳細を返す。
     *
     * @param examples 例のクラス
     * @return 違反の詳細
     */
    private static List<String> exampleDetails(Class<?>... examples) {
        JavaClasses classes = new ClassFileImporter().importClasses(examples);
        return tokenAccessRule(EXAMPLE_PRESENTATION_PACKAGE).evaluate(classes).getFailureReport().getDetails();
    }

    // ============================== 規則の補助 ==============================

    /**
     * token の取り出しを使える場所を、presentation 層、common.security、許可の一覧のクラスだけにする規則を作る。
     *
     * @param presentationPackage presentation 層のパッケージ（例：com.ecsite.module.*.presentation..）
     * @return 規則
     */
    private static ArchRule tokenAccessRule(String presentationPackage) {
        DescribedPredicate<JavaClass> allowedPlace = JavaClass.Predicates.resideInAnyPackage(presentationPackage, "com.ecsite.common.security..")
                .or(DescribedPredicate.describe("listed in the exceptions", javaClass -> TOKEN_ACCESS_EXCEPTIONS.contains(outermostName(javaClass))));
        return noClasses().that(DescribedPredicate.not(allowedPlace))
                .should().dependOnClassesThat(DescribedPredicate.describe(
                        "are token accessors", javaClass -> TOKEN_ACCESSORS.contains(javaClass.getName())));
    }
}
