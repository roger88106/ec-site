package com.ecsite.module.catalog.domain.category;

import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.common.model.base.AggregateRoot;
import com.ecsite.common.model.value.BoundedText;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.jmolecules.ddd.annotation.Identity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * カテゴリの Aggregate Root。<br />
 * 親カテゴリ、名称、階層の深さ、兄弟の中の表示順を持つ（docs/modules/catalog.md）。
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
     * 階層の最大の深さ（AC-9）
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
    private Category(@NonNull UUID id, UUID parentId, String name, int depth, int displayOrder, long version) {
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
     * カテゴリを作る（AC-8、9）。
     *
     * @param id           カテゴリ ID
     * @param parent       親カテゴリ。最上位なら null
     * @param name         名称
     * @param displayOrder 表示順
     * @return カテゴリ
     */
    static Category create(UUID id, Category parent, String name, int displayOrder) {
        if (parent == null) {
            return new Category(id, null, name, 1, displayOrder, 0);
        }
        return new Category(id, parent.id, name, parent.depth + 1, displayOrder, 0);
    }

// ============================== ドメインの振る舞い ==============================

    /**
     * 名称を変更する（AC-11）。
     *
     * @param name 新しい名称
     */
    public void rename(String name) {
        this.name = verifyName(name);
    }

    /**
     * 兄弟の並び順を変更する（AC-11）。<br />
     * 送られた順に 1 から表示順を振り直す。兄弟と過不足なく一致しなければ、何も変えずに業務のエラーを投げる。
     *
     * @param siblings   同じ親を持つ、今のカテゴリのすべて
     * @param orderedIds 並べたい順のカテゴリ ID
     * @return 表示順が変わったカテゴリ
     */
    public static List<Category> reorder(@NonNull List<Category> siblings, @NonNull List<UUID> orderedIds) {
        var siblingsById = siblings.stream().collect(Collectors.toMap(Category::id, Function.identity()));
        if (orderedIds.size() != siblingsById.size()
                || !siblingsById.keySet().equals(new HashSet<>(orderedIds))) {
            throw BusinessException.of(CategoryErrorCode.INVALID_CATEGORY_ORDER);
        }

        var changed = new ArrayList<Category>();
        for (int i = 0; i < orderedIds.size(); i++) {
            var category = siblingsById.get(orderedIds.get(i));
            int displayOrder = i + 1;
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
    public record Snapshot(UUID id, UUID parentId, String name, int depth, int displayOrder, long version) {
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
