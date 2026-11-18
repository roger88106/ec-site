package com.ecsite.architecture;

import com.ecsite.architecture.sample.entityequality.AllFieldsEntity;
import com.ecsite.architecture.sample.entityequality.EqualsOnlyEntity;
import com.ecsite.architecture.sample.entityequality.ExtraFieldEntity;
import com.ecsite.architecture.sample.entityequality.HashCodeOnlyEntity;
import com.ecsite.architecture.sample.entityequality.InheritingAggregate;
import com.ecsite.architecture.sample.entityequality.NoEqualsEntity;
import com.ecsite.architecture.sample.entityequality.NoIdentityAggregate;
import com.ecsite.architecture.sample.entityequality.PlainClass;
import com.ecsite.architecture.sample.entityequality.RecordEntity;
import com.ecsite.architecture.sample.entityequality.ValidEntity;
import com.ecsite.architecture.sample.entityequality.ValidGetterEntity;
import com.ecsite.common.model.base.AggregateRoot;
import com.ecsite.support.ProductionClasses;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.jmolecules.ddd.annotation.Entity;
import org.jmolecules.ddd.annotation.Identity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * entity と集約の同一性の検査
 *
 * <p>根拠はバックエンドの書き方の「entity と集約の同一性」。
 * jMolecules の Entity か AggregateRoot の注釈が付いたクラスと、基底クラスの {@code AggregateRoot} を継ぐクラスは、
 * record にせず、{@code equals} と {@code hashCode} を宣言し、{@code @Identity} の項目を持ち、
 * {@code equals} と {@code hashCode} が自分のクラスの項目のうち {@code @Identity} の項目だけを使う（項目の参照と、自分のクラスのメソッド（getter）の呼び出しから調べる）。
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
    @DisplayName("entity と集約の equals と hashCode は、@Identity の項目だけを使う")
    void entitiesCompareOnlyIdentityFields() {
        // 準備：対象のクラスが 1 件以上ある
        ProductionClasses.requireNotEmpty(targets(ProductionClasses.handwritten()), "entity と集約のクラス");

        // 操作と確認：本番のクラスに、@Identity でない項目を equals か hashCode で使うものがない
        assertThat(nonIdentityFieldsUsed(ProductionClasses.handwritten()))
                .as("equals か hashCode が @Identity でない項目を使う entity と集約（Lombok の @EqualsAndHashCode(onlyExplicitlyIncluded = true) と @EqualsAndHashCode.Include にする）")
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
                ValidGetterEntity.class.getName(),
                RecordEntity.class.getName(),
                NoEqualsEntity.class.getName(),
                HashCodeOnlyEntity.class.getName(),
                EqualsOnlyEntity.class.getName(),
                AllFieldsEntity.class.getName(),
                ExtraFieldEntity.class.getName(),
                NoIdentityAggregate.class.getName(),
                InheritingAggregate.class.getName());
    }

    @Test
    @DisplayName("record の規則は、record の例だけに当たる")
    void recordRuleMatchesOnlyRecords() {
        assertThat(names(records(examples()))).containsExactly(RecordEntity.class.getName());
    }

    @Test
    @DisplayName("equals と hashCode の規則は、どちらかを宣言していない class の例だけが該当する")
    void equalsRuleMatchesOnlyClassesWithoutBoth() {
        // 操作と確認：record の例は、record の規則で扱うので当たらない
        assertThat(names(withoutEqualsOrHashCode(examples()))).containsExactlyInAnyOrder(
                NoEqualsEntity.class.getName(),
                HashCodeOnlyEntity.class.getName(),
                EqualsOnlyEntity.class.getName(),
                InheritingAggregate.class.getName());
    }

    @Test
    @DisplayName("@Identity の規則は、@Identity の項目がない class の例だけが該当する")
    void identityRuleMatchesOnlyClassesWithoutIdentityField() {
        // 操作と確認：record の例は、record の規則で扱うので当たらない
        assertThat(names(withoutIdentity(examples()))).containsExactlyInAnyOrder(
                NoIdentityAggregate.class.getName(),
                InheritingAggregate.class.getName());
    }

    @Test
    @DisplayName("識別子だけで比べる規則は、@Identity でない項目を equals か hashCode で使う例だけが該当する")
    void identityOnlyRuleMatchesOnlyClassesUsingOtherFields() {
        // 操作と確認：全ての項目で比べる例（getter 経由）、hashCode が他の項目も使う例、@Identity を付けていない項目を比べる例が該当する。
        // getter と canEqual を使って識別子だけで比べる例は当たらない
        assertThat(nonIdentityFieldsUsed(examples())).containsExactly(
                AllFieldsEntity.class.getName() + ".name",
                ExtraFieldEntity.class.getName() + ".name",
                NoIdentityAggregate.class.getName() + ".id");
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
                ValidGetterEntity.class,
                RecordEntity.class,
                NoEqualsEntity.class,
                HashCodeOnlyEntity.class,
                EqualsOnlyEntity.class,
                AllFieldsEntity.class,
                ExtraFieldEntity.class,
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
     * equals か hashCode が、@Identity でない項目を使う class の対象を、"クラス名.項目名" の形で返す。
     *
     * <p>項目の参照と、自分のクラスのメソッド（getter）の呼び出し先の項目の参照を調べる。
     * Lombok が作る {@code canEqual} は項目を使わないので、呼び出しても影響しない。
     *
     * @param classes 調べるクラス
     * @return 名前の順の、"クラス名.項目名"
     */
    private static List<String> nonIdentityFieldsUsed(JavaClasses classes) {
        return targets(classes).stream()
                .filter(javaClass -> !javaClass.isRecord())
                .flatMap(javaClass -> usedFields(javaClass).stream()
                        .filter(field -> !field.isAnnotatedWith(Identity.class))
                        .map(field -> javaClass.getName() + "." + field.getName()))
                .sorted()
                .toList();
    }

    /**
     * equals(Object) と hashCode() が使う、自分のクラスの項目を返す。
     *
     * @param javaClass クラス
     * @return 直接参照する項目と、自分のクラスのメソッドの呼び出し先が参照する項目（static の項目を除く）
     */
    private static Set<JavaField> usedFields(JavaClass javaClass) {
        Set<JavaField> fields = new TreeSet<>(Comparator.comparing(JavaField::getName));
        javaClass.getMethods().stream()
                .filter(method -> isEquals(method) || isHashCode(method))
                .forEach(method -> {
                    fieldsAccessedBy(javaClass, method).forEach(fields::add);
                    method.getMethodCallsFromSelf().stream()
                            .filter(call -> call.getTargetOwner().getName().equals(javaClass.getName()))
                            .flatMap(call -> call.getTarget().resolveMember().stream())
                            .filter(callee -> !callee.getName().equals("equals") && !callee.getName().equals("hashCode"))
                            .flatMap(callee -> fieldsAccessedBy(javaClass, callee))
                            .forEach(fields::add);
                });
        return fields;
    }

    /**
     * メソッドが参照する、自分のクラスの項目（static を除く）を返す。
     *
     * @param javaClass 項目を持つクラス
     * @param method    メソッド
     * @return 参照する項目
     */
    private static Stream<JavaField> fieldsAccessedBy(JavaClass javaClass, JavaMethod method) {
        return method.getFieldAccesses().stream()
                .filter(access -> access.getTargetOwner().getName().equals(javaClass.getName()))
                .flatMap(access -> access.getTarget().resolveMember().stream())
                .filter(field -> !field.getModifiers().contains(JavaModifier.STATIC));
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
        return javaClass.getMethods().stream().anyMatch(EntityEqualityTest::isEquals);
    }

    /**
     * equals(Object) かを返す。
     *
     * @param method メソッド
     * @return equals(Object) なら true
     */
    private static boolean isEquals(JavaMethod method) {
        return method.getName().equals("equals")
                && method.getRawParameterTypes().size() == 1
                && method.getRawParameterTypes().get(0).isEquivalentTo(Object.class);
    }

    /**
     * hashCode() かを返す。
     *
     * @param method メソッド
     * @return hashCode() なら true
     */
    private static boolean isHashCode(JavaMethod method) {
        return method.getName().equals("hashCode") && method.getRawParameterTypes().isEmpty();
    }

    /**
     * hashCode() を自分で宣言しているかを返す。
     *
     * @param javaClass クラス
     * @return 宣言していれば true
     */
    private static boolean declaresHashCode(JavaClass javaClass) {
        return javaClass.getMethods().stream().anyMatch(EntityEqualityTest::isHashCode);
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
