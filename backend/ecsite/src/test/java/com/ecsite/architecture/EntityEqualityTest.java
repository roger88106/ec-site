package com.ecsite.architecture;

import com.ecsite.architecture.sample.entityequality.HashCodeOnlyEntity;
import com.ecsite.architecture.sample.entityequality.InheritingAggregate;
import com.ecsite.architecture.sample.entityequality.NoEqualsEntity;
import com.ecsite.architecture.sample.entityequality.NoIdentityAggregate;
import com.ecsite.architecture.sample.entityequality.PlainClass;
import com.ecsite.architecture.sample.entityequality.RecordEntity;
import com.ecsite.architecture.sample.entityequality.ValidEntity;
import com.ecsite.common.model.base.AggregateRoot;
import com.ecsite.support.ProductionClasses;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.jmolecules.ddd.annotation.Entity;
import org.jmolecules.ddd.annotation.Identity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * entity と集約の同一性の検査
 *
 * <p>根拠はバックエンドの書き方の「entity と集約の同一性」。
 * jMolecules の Entity か AggregateRoot の注釈が付いたクラスと、基底クラスの {@code AggregateRoot} を継ぐクラスは、
 * record にせず、{@code equals} と {@code hashCode} を宣言し、{@code @Identity} の項目を持つ。
 * コンパイルした後のクラスを読むので、Lombok が作った {@code equals} と {@code hashCode} も宣言として見える。
 * record の成分の注釈は、class に変えた後は項目の注釈になる。
 */
class EntityEqualityTest {

    // ============================== テスト ==============================

    @Test
    @DisplayName("entity と集約は、record でない")
    void entitiesAreNotRecords() {
        // 準備：対象のクラスが 1 件以上ある
        ProductionClasses.requireNotEmpty(targets(ProductionClasses.handwritten()), "entity と集約のクラス");

        // 操作と確認：本番のクラスに、record の entity と集約がない
        assertThat(names(records(ProductionClasses.handwritten())))
                .as("record の entity と集約（class にして Lombok の equals を付ける）")
                .isEmpty();
    }

    @Test
    @DisplayName("entity と集約は、equals と hashCode を自分で宣言している")
    void entitiesDeclareEqualsAndHashCode() {
        // 準備：対象のクラスが 1 件以上ある
        ProductionClasses.requireNotEmpty(targets(ProductionClasses.handwritten()), "entity と集約のクラス");

        // 操作と確認：本番のクラスに、equals か hashCode を宣言していないものがない
        assertThat(names(withoutEqualsOrHashCode(ProductionClasses.handwritten())))
                .as("equals と hashCode を宣言していない entity と集約（Lombok の @EqualsAndHashCode を付ける）")
                .isEmpty();
    }

    @Test
    @DisplayName("entity と集約は、@Identity の項目を持つ")
    void entitiesHaveIdentityField() {
        // 準備：対象のクラスが 1 件以上ある
        ProductionClasses.requireNotEmpty(targets(ProductionClasses.handwritten()), "entity と集約のクラス");

        // 操作と確認：本番のクラスに、@Identity の項目を持たないものがない
        assertThat(names(withoutIdentity(ProductionClasses.handwritten())))
                .as("@Identity の項目を持たない entity と集約")
                .isEmpty();
    }

    @Test
    @DisplayName("対象は、注釈の付いたクラスと基底クラスを継ぐクラスだけ")
    void targetsAreAnnotatedOrInheriting() {
        // 準備：規則に合う例、違反する例（注釈のみ・継承のみを含む）、対象でない例
        var examples = examples();

        // 操作と確認：対象でない例だけが外れる
        assertThat(names(targets(examples))).containsExactlyInAnyOrder(
                ValidEntity.class.getName(),
                RecordEntity.class.getName(),
                NoEqualsEntity.class.getName(),
                HashCodeOnlyEntity.class.getName(),
                NoIdentityAggregate.class.getName(),
                InheritingAggregate.class.getName());
    }

    @Test
    @DisplayName("record の規則は、record の例だけに当たる")
    void recordRuleMatchesOnlyRecords() {
        assertThat(names(records(examples()))).containsExactly(RecordEntity.class.getName());
    }

    @Test
    @DisplayName("equals と hashCode の規則は、どちらかを宣言していない class の例だけに当たる")
    void equalsRuleMatchesOnlyClassesWithoutBoth() {
        // 操作と確認：record の例は、record の規則で扱うので当たらない
        assertThat(names(withoutEqualsOrHashCode(examples()))).containsExactlyInAnyOrder(
                NoEqualsEntity.class.getName(),
                HashCodeOnlyEntity.class.getName(),
                InheritingAggregate.class.getName());
    }

    @Test
    @DisplayName("@Identity の規則は、@Identity の項目がない class の例だけに当たる")
    void identityRuleMatchesOnlyClassesWithoutIdentityField() {
        // 操作と確認：record の例は、record の規則で扱うので当たらない
        assertThat(names(withoutIdentity(examples()))).containsExactlyInAnyOrder(
                NoIdentityAggregate.class.getName(),
                InheritingAggregate.class.getName());
    }

    // ============================== 補助 ==============================

    /**
     * 規則の例のクラスを読み込む。
     *
     * @return 規則に合う例、違反する例、対象でない例
     */
    private static JavaClasses examples() {
        return new ClassFileImporter().importClasses(
                ValidEntity.class,
                RecordEntity.class,
                NoEqualsEntity.class,
                HashCodeOnlyEntity.class,
                NoIdentityAggregate.class,
                InheritingAggregate.class,
                PlainClass.class);
    }

    /**
     * record の対象のクラスを返す。
     *
     * @param classes 調べるクラス
     * @return record の entity と集約
     */
    private static List<JavaClass> records(JavaClasses classes) {
        return targets(classes).stream().filter(JavaClass::isRecord).toList();
    }

    /**
     * equals か hashCode を宣言していない class の対象を返す。
     *
     * @param classes 調べるクラス
     * @return {@code equals(Object)} と {@code hashCode()} のどちらかを自分で宣言していない、record でない entity と集約
     */
    private static List<JavaClass> withoutEqualsOrHashCode(JavaClasses classes) {
        return targets(classes).stream()
                .filter(javaClass -> !javaClass.isRecord())
                .filter(javaClass -> !declaresEquals(javaClass) || !declaresHashCode(javaClass))
                .toList();
    }

    /**
     * Identity の項目がない class の対象を返す。
     *
     * @param classes 調べるクラス
     * @return {@code @Identity} の付いた項目を持たない、record でない entity と集約
     */
    private static List<JavaClass> withoutIdentity(JavaClasses classes) {
        return targets(classes).stream()
                .filter(javaClass -> !javaClass.isRecord())
                .filter(javaClass -> javaClass.getFields().stream().noneMatch(field -> field.isAnnotatedWith(Identity.class)))
                .toList();
    }

    /**
     * 規則の対象の、entity と集約のクラスを返す。
     *
     * @param classes 調べるクラス
     * @return jMolecules の Entity か AggregateRoot の注釈が付いたクラスと、基底クラスの {@code AggregateRoot} を継ぐクラス
     */
    private static List<JavaClass> targets(JavaClasses classes) {
        return classes.stream()
                .filter(javaClass -> javaClass.isAnnotatedWith(Entity.class)
                        || javaClass.isAnnotatedWith(org.jmolecules.ddd.annotation.AggregateRoot.class)
                        || (javaClass.isAssignableTo(AggregateRoot.class) && !javaClass.isEquivalentTo(AggregateRoot.class)))
                .sorted(Comparator.comparing(JavaClass::getName))
                .toList();
    }

    /**
     * equals(Object) を自分で宣言しているかを返す。
     *
     * @param javaClass クラス
     * @return 宣言していれば true
     */
    private static boolean declaresEquals(JavaClass javaClass) {
        return javaClass.getMethods().stream()
                .anyMatch(method -> method.getName().equals("equals")
                        && method.getRawParameterTypes().size() == 1
                        && method.getRawParameterTypes().get(0).isEquivalentTo(Object.class));
    }

    /**
     * hashCode() を自分で宣言しているかを返す。
     *
     * @param javaClass クラス
     * @return 宣言していれば true
     */
    private static boolean declaresHashCode(JavaClass javaClass) {
        return javaClass.getMethods().stream()
                .anyMatch(method -> method.getName().equals("hashCode") && method.getRawParameterTypes().isEmpty());
    }

    /**
     * クラスの名前を、名前の順で返す。
     *
     * @param classes クラス
     * @return クラスの名前
     */
    private static List<String> names(List<JavaClass> classes) {
        return classes.stream().map(JavaClass::getName).sorted().toList();
    }
}
