package com.ecsite.architecture;

import com.ecsite.ProductionClasses;
import com.ecsite.architecture.money.module.sample.application.model.PricedRow;
import com.ecsite.architecture.money.module.sample.application.model.PricedView;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 金額を整数で持つ項目の検査（docs/rules/backend-coding.md 第 1.5 節）。<br />
 * モジュールの間の公開の型（api・event）と、command・表示用（view）の型の金額は {@code Money} にする。
 * 整数で持つのは presentation（API の型との変換）と infrastructure（DB）だけ。
 */
class MoneyTypeTest {

    /**
     * 金額の項目の名前（価格、金額、手数料、合計）。{@code totalQuantity} のような数量は含めない
     */
    private static final Pattern MONEY_NAME = Pattern.compile("(?i).*(price|amount|fee)(InclTax|ExclTax)?|total|.*Total");

    /**
     * 整数の型
     */
    private static final Set<String> INTEGER_TYPES = Set.of("long", "int", "java.lang.Long", "java.lang.Integer");

    /**
     * モジュールの公開の型のパッケージ（api・event とその下）の形
     */
    private static final Pattern PUBLIC_PACKAGE = Pattern.compile(".*\\.module\\.[^.]+\\.(api|event)(\\..*)?");

    /**
     * command・view を置く application.model（とその下）のパッケージの形
     */
    private static final Pattern MODEL_PACKAGE = Pattern.compile(".*\\.module\\.[^.]+\\.application\\.model(\\..*)?");

    @Test
    @DisplayName("api・event の型と command・view に、金額を整数で持つ項目がない（対象は 1 件以上ある）")
    void moneyIsNotAnInteger() {
        ProductionClasses.requireNotEmpty(checkedClasses(ProductionClasses.modules()), "api・event のクラスと、application.model の View・Command");

        assertThat(violations(ProductionClasses.modules())).isEmpty();
    }

    @Test
    @DisplayName("規則は、application.model の直下の view にある整数の価格に当たり、名前が View・Command でないクラスには当たらない")
    void ruleMatchesIntegerPriceInViewDirectlyUnderModel() {
        JavaClasses examples = new ClassFileImporter().importClasses(PricedView.class, PricedRow.class);

        assertThat(violations(examples)).containsExactly(PricedView.class.getName() + ".price");
    }

    /**
     * 金額の検査の対象のクラスを返す。
     *
     * @param classes 調べるクラス
     * @return api・event のクラスと、application.model 以下の名前が View・Command で終わるクラス（入れ子のクラスを含む）
     */
    private static List<JavaClass> checkedClasses(JavaClasses classes) {
        return classes.stream()
                .filter(javaClass -> PUBLIC_PACKAGE.matcher(javaClass.getPackageName()).matches()
                        || (MODEL_PACKAGE.matcher(javaClass.getPackageName()).matches() && isViewOrCommand(javaClass)))
                .toList();
    }

    /**
     * 名前が View か Command で終わる最上位のクラス（またはその入れ子のクラス）かを返す。
     *
     * @param javaClass クラス
     * @return 最上位のクラスの名前が View か Command で終わるなら true
     */
    private static boolean isViewOrCommand(JavaClass javaClass) {
        String topLevelName = javaClass.getName().split("\\$", 2)[0];
        return topLevelName.endsWith("View") || topLevelName.endsWith("Command");
    }

    /**
     * 金額を整数で持つ項目を返す。
     *
     * @param classes 調べるクラス
     * @return 検査の対象のクラスの、金額の名前で整数の static でない項目（項目の名前の順）
     */
    private static List<String> violations(JavaClasses classes) {
        return checkedClasses(classes).stream()
                .flatMap(javaClass -> javaClass.getFields().stream())
                .filter(field -> !field.getModifiers().contains(JavaModifier.STATIC))
                .filter(field -> MONEY_NAME.matcher(field.getName()).matches())
                .filter(field -> INTEGER_TYPES.contains(field.getRawType().getName()))
                .map(JavaField::getFullName)
                .sorted()
                .toList();
    }
}
