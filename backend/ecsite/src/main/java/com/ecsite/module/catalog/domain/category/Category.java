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
 * カテゴリ
 *
 * <p>親カテゴリ、名称、階層の深さ、兄弟の中の表示順を持つ集約のルート。
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
     * @throws BusinessException 深さが上限を超える（{@code CATEGORY_DEPTH_EXCEEDED}）
     * @throws BusinessException 名称が範囲の外（{@code INVALID_CATEGORY_NAME}）
     */
    private Category(@NonNull UUID id, @Nullable UUID parentId, String name, int depth, int displayOrder, long version) {
        if (depth < 1 || depth > MAX_DEPTH) {
            throw BusinessException.of(CategoryErrorCode.CATEGORY_DEPTH_EXCEEDED, List.of(MAX_DEPTH));
        }
        if ((parentId == null) != (depth == 1)) {
            throw new IllegalArgumentException("最上位のカテゴリだけが深さ 1 になる：parentId=%s, depth=%d".formatted(parentId, depth));
        }
        verifyName(name);
        this.id = id;
        this.parentId = parentId;
        this.name = name;
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
     * @throws BusinessException 深さが上限を超える（{@code CATEGORY_DEPTH_EXCEEDED}）
     * @throws BusinessException 名称が範囲の外（{@code INVALID_CATEGORY_NAME}）
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
     * @throws BusinessException 名称が範囲の外（{@code INVALID_CATEGORY_NAME}）
     */
    public void rename(String name) {
        verifyName(name);
        this.name = name;
    }

    /**
     * その深さのカテゴリの下に、子のカテゴリを追加できるかを返す。
     *
     * @param depth 階層の深さ（最上位が 1）
     * @return いちばん深い階層でなければ true
     */
    public static boolean canHaveChildren(int depth) {
        return depth < MAX_DEPTH;
    }

    /**
     * 兄弟の並び順を変更する。
     *
     * <p>送られた順に 1 から表示順を振り直す。
     *
     * @param siblings   同じ親を持つ、今のカテゴリの全て
     * @param orderedIds 並べたい順のカテゴリ ID
     * @return 表示順が変わったカテゴリ
     * @throws BusinessException 兄弟と過不足なく一致しない（{@code INVALID_CATEGORY_ORDER}）。このときは何も変えない
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
     * @throws BusinessException 名称が範囲の外（{@code INVALID_CATEGORY_NAME}）
     */
    private static void verifyName(String name) {
        if (!BoundedText.isFilledWithin(name, NAME_MAX_LENGTH)) {
            throw BusinessException.of(CategoryErrorCode.INVALID_CATEGORY_NAME, List.of(NAME_MAX_LENGTH));
        }
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
