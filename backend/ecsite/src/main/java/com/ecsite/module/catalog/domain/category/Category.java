package com.ecsite.module.catalog.domain.category;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.base.AggregateRoot;
import com.ecsite.common.model.value.BoundedText;
import com.ecsite.module.catalog.domain.CategoryErrorCode;
import com.ecsite.module.catalog.domain.DisplayOrders;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.jmolecules.ddd.annotation.Identity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * カテゴリの Aggregate Root。<br />
 * 親カテゴリ、名称、階層の深さ、兄弟の中の表示順を持つ（docs/design/modules/catalog.md）。
 */
@Getter
@Accessors(fluent = true)
@org.jmolecules.ddd.annotation.AggregateRoot
public class Category extends AggregateRoot {

    /**
     * 名称の最大の文字数
     */
    static final int NAME_MAX_LENGTH = 50;

    /**
     * 階層の最大の深さ
     */
    static final int MAX_DEPTH = 3;

// ============================== フィールド定義 ==============================

    /**
     * カテゴリ ID
     */
    @Identity
    private final UUID id;

    /**
     * 親カテゴリ ID。最上位なら null
     */
    @Nullable
    private final UUID parentId;

    /**
     * 名称
     */
    private String name;

    /**
     * 階層の深さ（最上位が 1）
     */
    private final int depth;

    /**
     * 兄弟の中の表示順（小さいほど先）
     */
    private int displayOrder;

    /**
     * バージョン（楽観ロック）。リポジトリが保存するときに上げる
     */
    private final long version;

// ============================== コンストラクタ ==============================

    /**
     * 全属性コンストラクタ
     *
     * @param id           カテゴリ ID
     * @param parentId     親カテゴリ ID。最上位なら null
     * @param name         名称
     * @param depth        階層の深さ
     * @param displayOrder 表示順
     * @param version      バージョン
     */
    private Category(@NonNull UUID id, @Nullable UUID parentId, String name, int depth, int displayOrder, long version) {
        if (depth < 1 || depth > MAX_DEPTH) {
            throw BusinessException.of(CategoryErrorCode.CATEGORY_DEPTH_EXCEEDED, List.of(MAX_DEPTH));
        }
        if ((parentId == null) != (depth == 1)) {
            throw new IllegalArgumentException("最上位のカテゴリだけが深さ 1 になる：parentId=%s, depth=%d".formatted(parentId, depth));
        }
        this.id = id;
        this.parentId = parentId;
        this.name = verifyName(name);
        this.depth = depth;
        this.displayOrder = displayOrder;
        this.version = version;
    }

    /**
     * カテゴリを作る。
     *
     * @param id           カテゴリ ID
     * @param parent       親カテゴリ。最上位なら null
     * @param name         名称
     * @param displayOrder 表示順
     * @return カテゴリ
     */
    static Category create(UUID id, @Nullable Category parent, String name, int displayOrder) {
        if (parent == null) {
            return new Category(id, null, name, 1, displayOrder, 0);
        }
        return new Category(id, parent.id, name, parent.depth + 1, displayOrder, 0);
    }

// ============================== ドメインの振る舞い ==============================

    /**
     * 名称を変更する。
     *
     * @param name 新しい名称
     */
    public void rename(String name) {
        this.name = verifyName(name);
    }

    /**
     * その深さのカテゴリの下に、子のカテゴリを追加できるかを返す。カテゴリ（集約）を読み込まないクエリでも使う。
     *
     * @param depth 階層の深さ（最上位が 1）
     * @return いちばん深い階層でなければ true
     */
    public static boolean canHaveChildren(int depth) {
        return depth < MAX_DEPTH;
    }

    /**
     * 兄弟の並び順を変更する。<br />
     * 送られた順に 1 から表示順を振り直す。兄弟と過不足なく一致しなければ、何も変えずに業務のエラーを投げる。
     *
     * @param siblings   同じ親を持つ、今のカテゴリのすべて
     * @param orderedIds 並べたい順のカテゴリ ID
     * @return 表示順が変わったカテゴリ
     */
    public static List<Category> reorder(@NonNull List<Category> siblings, @NonNull List<UUID> orderedIds) {
        var orders = DisplayOrders.assign(siblings.stream().map(Category::id).toList(), orderedIds,
                CategoryErrorCode.INVALID_CATEGORY_ORDER);
        var changed = new ArrayList<Category>();
        for (var category : siblings) {
            int displayOrder = Objects.requireNonNull(orders.get(category.id), "並び順は同じ親の全てのカテゴリに付く");
            if (category.displayOrder != displayOrder) {
                category.displayOrder = displayOrder;
                changed.add(category);
            }
        }
        return changed;
    }

// ============================== プライベートメソッド ==============================

    /**
     * 名称が 1 文字以上 50 文字以内（空白のみは不可）であることを検証する。
     *
     * @param name 名称
     * @return 名称
     */
    private static String verifyName(String name) {
        if (!BoundedText.isFilledWithin(name, NAME_MAX_LENGTH)) {
            throw BusinessException.of(CategoryErrorCode.INVALID_CATEGORY_NAME, List.of(NAME_MAX_LENGTH));
        }
        return name;
    }

// ============================== スナップショット関連処理 ==============================

    /**
     * カテゴリのスナップショット
     *
     * @param id           カテゴリ ID
     * @param parentId     親カテゴリ ID。最上位なら null
     * @param name         名称
     * @param depth        階層の深さ
     * @param displayOrder 表示順
     * @param version      バージョン
     */
    public record Snapshot(UUID id, @Nullable UUID parentId, String name, int depth, int displayOrder, long version) {
    }

    /**
     * スナップショットを取得する
     *
     * @return カテゴリのスナップショット
     */
    public Snapshot toSnapshot() {
        return new Snapshot(id, parentId, name, depth, displayOrder, version);
    }

    /**
     * スナップショットから復元する
     *
     * @param snapshot カテゴリのスナップショット
     */
    Category(Snapshot snapshot) {
        this(snapshot.id(), snapshot.parentId(), snapshot.name(), snapshot.depth(), snapshot.displayOrder(), snapshot.version());
    }
}
