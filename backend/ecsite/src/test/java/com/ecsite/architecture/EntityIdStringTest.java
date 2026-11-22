package com.ecsite.architecture;

import com.ecsite.architecture.sample.identifier.module.sample.domain.ExampleId;
import com.ecsite.common.model.base.EntityId;
import com.ecsite.common.model.id.ProductId;
import com.ecsite.support.ProductionClasses;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 識別子の型の文字列の形の検査
 *
 * <p>{@link EntityId} を実装する record の {@code toString()} は、値の UUID の文字列を返す。
 * 文字列に連結する所（メッセージ、ログ、ファイルのキー）で、UUID の文字列のままにするため。
 * record の既定の形（{@code ProductId[value=...]}）のままの型を見つける。
 */
class EntityIdStringTest {

    // ============================== 定数 ==============================

    /**
     * 識別子の型を作るときの値
     */
    private static final UUID VALUE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    // ============================== テスト ==============================

    @Test
    @DisplayName("本番の全ての識別子の型の toString は、値の UUID の文字列を返す")
    void productionIdsReturnUuidString() {
        // 準備：検査の対象の識別子の型が 1 件以上ある
        ProductionClasses.requireNotEmpty(entityIds(ProductionClasses.handwritten()), "識別子の型");

        // 操作と確認：toString が UUID の文字列でない型がない
        assertThat(violations(ProductionClasses.handwritten())).isEmpty();
    }

    @Test
    @DisplayName("規則は、toString を上書きしない識別子の型に当たり、上書きした型には当たらない")
    void ruleMatchesOnlyIdsWithoutOverride() {
        // 準備：上書きしない例と、上書きした本番の型
        JavaClasses examples = new ClassFileImporter().importClasses(ExampleId.class, ProductId.class);

        // 操作と確認：上書きしない例だけが違反になる
        assertThat(violations(examples)).containsExactly(ExampleId.class.getName());
    }

    // ============================== 補助 ==============================

    /**
     * 識別子の型（{@link EntityId} を実装する record）を返す。
     *
     * @param classes 調べるクラス
     * @return 識別子の型
     */
    private static List<JavaClass> entityIds(JavaClasses classes) {
        return classes.stream()
                .filter(javaClass -> javaClass.isRecord() && javaClass.isAssignableTo(EntityId.class))
                .toList();
    }

    /**
     * toString が値の UUID の文字列を返さない識別子の型を返す。
     *
     * @param classes 調べるクラス
     * @return 識別子の型の完全な名前（名前の順）
     */
    private static List<String> violations(JavaClasses classes) {
        return entityIds(classes).stream()
                .filter(javaClass -> !create(javaClass).toString().equals(VALUE.toString()))
                .map(JavaClass::getName)
                .sorted()
                .toList();
    }

    /**
     * 識別子の型を、決まった値で作る。
     *
     * @param javaClass 識別子の型
     * @return 識別子
     */
    private static EntityId create(JavaClass javaClass) {
        try {
            var constructor = javaClass.reflect().getDeclaredConstructor(UUID.class);
            constructor.setAccessible(true);
            return (EntityId) constructor.newInstance(VALUE);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("識別子の型を作れない：" + javaClass.getName(), e);
        }
    }
}
