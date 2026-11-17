package com.ecsite.architecture;

import com.ecsite.architecture.sample.enumconversion.SampleKind;
import com.ecsite.architecture.sample.enumconversion.infrastructure.persistence.ReadsEnumFromColumn;
import com.ecsite.architecture.sample.enumconversion.presentation.ConvertsEnumFromString;
import com.ecsite.support.ProductionClasses;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
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
 * 列挙型の {@code valueOf(String)} と {@code fromValue} を呼ぶのは、domain と永続化（infrastructure.persistence）だけである。
 * 永続化は DB の文字列を読むため。
 * モジュールやレイヤーの間で列挙を文字列で受け渡すと、相手に値を追加したときに気づけないため。
 * 呼び出しとメソッド参照を、クラスファイルから型で探す（呼ぶ先が列挙型かどうかで決めるので、列挙でない型の valueOf は当たらない）。
 */
class EnumStringConversionTest {

    // ============================== 定数 ==============================

    /**
     * 名前の文字列から変換してよい場所
     */
    private static final DescribedPredicate<JavaClass> ALLOWED = JavaClass.Predicates.resideInAnyPackage(
            "..domain..", "..infrastructure.persistence..");

    // ============================== テスト ==============================

    @Test
    @DisplayName("domain と永続化の外の本番のクラスは、列挙型を名前の文字列から変換しない")
    void noStringConversionOutsideDomainAndPersistence() {
        // 操作と確認：違反がない
        assertThat(violations(ProductionClasses.handwritten())).isEmpty();
    }

    @Test
    @DisplayName("規則は、列挙型の valueOf と fromValue の呼び出しとメソッド参照に当たり、永続化と列挙でない型には当たらない")
    void ruleMatchesOnlyEnumConversions() {
        // 準備：presentation で変換する例と、永続化で変換する例
        var examples = new ClassFileImporter().importClasses(
                ConvertsEnumFromString.class, ReadsEnumFromColumn.class, SampleKind.class);

        // 操作と確認：presentation の valueOf の呼び出しと fromValue のメソッド参照だけが違反になる
        assertThat(violations(examples)).containsExactly(
                ConvertsEnumFromString.class.getName() + " -> SampleKind.fromValue",
                ConvertsEnumFromString.class.getName() + " -> SampleKind.valueOf");
    }

    // ============================== 補助 ==============================

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
     * @return 呼ぶ先が列挙型の {@code valueOf(String)} か {@code fromValue} なら true
     */
    private static boolean convertsFromString(JavaAccess<?> access) {
        JavaClass target = access.getTargetOwner();
        if (!target.isEnum()) {
            return false;
        }
        return access.getName().equals("fromValue")
                || (access.getName().equals("valueOf") && access.getTarget().getFullName().endsWith("(java.lang.String)"));
    }
}
