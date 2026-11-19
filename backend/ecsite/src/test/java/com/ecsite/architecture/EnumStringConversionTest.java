package com.ecsite.architecture;

import com.ecsite.architecture.sample.enumconversion.SampleKind;
import com.ecsite.architecture.sample.enumconversion.infrastructure.persistence.ReadsEnumFromColumn;
import com.ecsite.architecture.sample.enumconversion.presentation.ConvertsEnumFromString;
import com.ecsite.architecture.sample.enumconversion.presentation.OverloadedValueOfKind;
import com.ecsite.common.conversion.EnumConversion;
import com.ecsite.support.ProductionClasses;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 列挙型を名前の文字列から変換する場所の検査
 *
 * <p>根拠はバックエンドの書き方の「他のモジュールは腐敗防止層で包む」。
 * 列挙型の {@code valueOf(String)}・{@code fromValue}・{@code Enum.valueOf(Class, String)} を呼んでよいのは、次の 4 つだけである。
 * domain、永続化（infrastructure.persistence）、共通の変換のメソッドのクラス（{@link EnumConversion}）、
 * HTTP のクエリの文字列を列挙にする設定の変換（WebMvcConfig の中のクラス）。
 * 永続化と設定の変換は、外から来る文字列を読むため。共通の変換のメソッドは、名前が同じ列挙の間の変換を 1 か所にまとめるため。
 * モジュールやレイヤーの間で列挙を文字列で受け渡すと、相手に値を追加したときに気づけないため。
 * 呼び出しとメソッド参照を、クラスファイルから型で探す（呼ぶ先が列挙型かどうかで決めるので、列挙でない型の valueOf は当たらない）。
 */
class EnumStringConversionTest {

    // ============================== 定数 ==============================

    /**
     * HTTP のクエリの文字列を列挙にする変換（WebMvcConfig の中の private のクラス）の名前
     */
    private static final String STRING_TO_ENUM_CONVERTER_FACTORY = "com.ecsite.config.WebMvcConfig$StringToEnumConverterFactory";

    /**
     * 名前の文字列から変換してよい場所
     *
     * <p>WebMvcConfig の文字列から列挙への変換を許すのは、外から来る HTTP の文字列を読み、変換先の型が実行時にしか決まらないため。
     */
    private static final DescribedPredicate<JavaClass> ALLOWED = JavaClass.Predicates.resideInAnyPackage(
            "..domain..", "..infrastructure.persistence..")
            .or(JavaClass.Predicates.belongToAnyOf(EnumConversion.class))
            .or(DescribedPredicate.describe(
                    "WebMvcConfig の文字列から列挙への変換",
                    javaClass -> javaClass.getName().startsWith(STRING_TO_ENUM_CONVERTER_FACTORY)));

    // ============================== テスト ==============================

    @Test
    @DisplayName("許す場所（domain、永続化、共通の変換のメソッド、設定の変換）の外の本番のクラスは、列挙型を名前の文字列から変換しない")
    void noStringConversionOutsideAllowedPlaces() {
        // 操作と確認：違反がない
        assertThat(violations(ProductionClasses.handwritten())).isEmpty();
    }

    @Test
    @DisplayName("規則は、列挙型の valueOf と fromValue と Enum.valueOf(Class, String) に当たり、永続化と共通の変換のメソッドと列挙でない型には当たらない")
    void ruleMatchesOnlyEnumConversions() {
        // 準備：presentation で変換する例と、永続化で変換する例と、共通の変換のメソッド
        var examples = new ClassFileImporter().importClasses(
                ConvertsEnumFromString.class, OverloadedValueOfKind.class, ReadsEnumFromColumn.class, SampleKind.class,
                EnumConversion.class);

        // 操作と確認：presentation の valueOf の呼び出し、Enum.valueOf の呼び出し（多重定義の valueOf の中も）、
        // fromValue のメソッド参照だけが違反になる
        assertThat(violations(examples)).containsExactly(
                ConvertsEnumFromString.class.getName() + " -> Enum.valueOf",
                ConvertsEnumFromString.class.getName() + " -> SampleKind.fromValue",
                ConvertsEnumFromString.class.getName() + " -> SampleKind.valueOf",
                OverloadedValueOfKind.class.getName() + " -> Enum.valueOf");
    }

    // ============================== 確認の補助 ==============================

    /**
     * 変換してよい場所の外で、列挙型を名前の文字列から変換する呼び出しとメソッド参照を返す。
     *
     * <p>列挙型が自分の中で変換するもの（{@code fromValue} から {@code valueOf} を呼ぶなど）は、列挙型の定義なので当たらない。
     *
     * @param classes 調べるクラス
     * @return 呼び出す側の最上位のクラスの名前と、変換の操作（名前の順）
     */
    private static List<String> violations(JavaClasses classes) {
        return classes.stream()
                .filter(javaClass -> !ALLOWED.test(javaClass))
                .flatMap(javaClass -> Stream.concat(
                        javaClass.getMethodCallsFromSelf().stream(), javaClass.getMethodReferencesFromSelf().stream()))
                .filter(EnumStringConversionTest::convertsFromString)
                .filter(access -> !access.getOriginOwner().equals(access.getTargetOwner()))
                .map(access -> ProductionClasses.outermostName(access.getOriginOwner()) + " -> "
                        + access.getTargetOwner().getSimpleName() + "." + access.getName())
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * 列挙型を名前の文字列から変換する操作へのアクセスかを返す。
     *
     * @param access 呼び出しかメソッド参照
     * @return 呼ぶ先が列挙型の {@code valueOf(String)} か {@code fromValue}、または {@code Enum.valueOf(Class, String)} なら true
     */
    private static boolean convertsFromString(JavaAccess<?> access) {
        JavaClass target = access.getTargetOwner();
        if (target.isEquivalentTo(Enum.class)) {
            // 列挙型ごとにコンパイラが作る valueOf(String) は、列挙型の定義なので除く（多重定義の valueOf は除かない）
            boolean definesOwnValueOf = access.getOriginOwner().isEnum()
                    && access.getOrigin().getModifiers().contains(JavaModifier.STATIC)
                    && access.getOrigin().getFullName().equals(access.getOriginOwner().getName() + ".valueOf(java.lang.String)");
            return access.getName().equals("valueOf") && !definesOwnValueOf;
        }
        if (!target.isEnum()) {
            return false;
        }
        return access.getName().equals("fromValue")
                || (access.getName().equals("valueOf") && access.getTarget().getFullName().endsWith("(java.lang.String)"));
    }
}
