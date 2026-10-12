package com.ecsite.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 金額を整数で持つ項目の検査（docs/style/backend.md 第 1.5 節、spec 005 DC-34）。<br />
 * モジュールの間の公開の型（api・event）と、command の型の金額は {@code Money} にする。
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
     * 調べるパッケージ（モジュールの api・event と、command）の形
     */
    private static final Pattern CHECKED_PACKAGE =
            Pattern.compile("com\\.ecsite\\.module\\.[^.]+\\.(api|event|application\\.model(\\.command)?)(\\..*)?");

    /**
     * 本番のクラス（テストを除く）
     */
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.ecsite.module");

    @Test
    @DisplayName("api・event の型と command に、金額を整数で持つ項目がない")
    void moneyIsNotAnInteger() {
        List<String> violations = CLASSES.stream()
                .filter(javaClass -> CHECKED_PACKAGE.matcher(javaClass.getPackageName()).matches())
                .filter(javaClass -> javaClass.getPackageName().contains(".api")
                        || javaClass.getPackageName().contains(".event")
                        || javaClass.getSimpleName().endsWith("Command"))
                .flatMap(javaClass -> javaClass.getFields().stream())
                .filter(field -> !field.getModifiers().contains(JavaModifier.STATIC))
                .filter(field -> MONEY_NAME.matcher(field.getName()).matches())
                .filter(field -> INTEGER_TYPES.contains(field.getRawType().getName()))
                .map(JavaField::getFullName)
                .sorted()
                .toList();

        assertThat(violations).isEmpty();
    }
}
