package com.ecsite.architecture;

import com.ecsite.support.ProductionClasses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * 注釈を付ける層の検査
 *
 * <p>根拠はバックエンドの書き方の「層とパッケージ」。
 * トランザクションとコントローラーの注釈を付けてよい層を、本番のクラスファイルから確認する。
 */
class AnnotationPlacementTest {

    // ============================== テスト ==============================

    @Nested
    @DisplayName("@Transactional")
    class TransactionalPlacement {

        @Test
        @DisplayName("@Transactional を付けるクラスは、application と infrastructure だけにある")
        void transactionalClassesOnlyInApplicationOrInfrastructure() {
            // 操作と確認：本番のクラスに規則を当てる
            // 注釈の型は除く。リスナーの注釈（ExternalServiceListener）は、@Transactional を重ねて持つ
            classes().that().areAnnotatedWith(Transactional.class)
                    .and().areNotAnnotations()
                    .should().resideInAnyPackage(
                            ProductionClasses.MODULE_PACKAGE + ".*.application..", ProductionClasses.MODULE_PACKAGE + ".*.infrastructure..")
                    .check(ProductionClasses.all());
        }

        @Test
        @DisplayName("@Transactional を付けるメソッドは、application と infrastructure のクラスだけにある")
        void transactionalMethodsOnlyInApplicationOrInfrastructure() {
            // 操作と確認：本番のクラスに規則を当てる
            methods().that().areAnnotatedWith(Transactional.class)
                    .should().beDeclaredInClassesThat()
                    .resideInAnyPackage(
                            ProductionClasses.MODULE_PACKAGE + ".*.application..", ProductionClasses.MODULE_PACKAGE + ".*.infrastructure..")
                    .check(ProductionClasses.all());
        }
    }

    @Test
    @DisplayName("@RestController は presentation だけに付ける（MeController を除く）")
    void restControllerOnlyInPresentation() {
        // 操作と確認：本番のクラスに規則を当てる
        classes().that().areAnnotatedWith(RestController.class)
                .and().resideInAPackage("com.ecsite..")
                .and().doNotHaveFullyQualifiedName(ArchitectureExceptions.ME_CONTROLLER)
                .should().resideInAPackage(ProductionClasses.MODULE_PACKAGE + ".*.presentation..")
                .check(ProductionClasses.all());
    }
}
