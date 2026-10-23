package com.ecsite.module.catalog.application.category;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.catalog.application.model.view.CategoryNodeView;
import com.ecsite.module.catalog.application.model.view.CategoryView;
import com.ecsite.module.catalog.application.port.CategoryQueryRepository;
import com.ecsite.module.catalog.domain.category.Category;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * カテゴリのクエリサービス
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryQueryService {

    /**
     * カテゴリクエリリポジトリ
     */
    private final CategoryQueryRepository categoryQueryRepository;

    /**
     * カテゴリの木を取得する。
     *
     * @return 最上位カテゴリの一覧（子孫を含む。各階層は並び順）
     */
    public List<CategoryNodeView> getCategoryTree() {
        // 親カテゴリ ID → 子カテゴリ（並び順を保つ）。最上位は null をキーにする
        Map<UUID, List<CategoryView>> childrenByParent = new HashMap<>();
        for (var category : categoryQueryRepository.findAll()) {
            childrenByParent.computeIfAbsent(category.parentId(), key -> new ArrayList<>()).add(category);
        }
        return toNodes(null, childrenByParent);
    }

    /**
     * カテゴリを取得する。
     *
     * @param id カテゴリ ID
     * @return カテゴリ（子カテゴリを含まない）
     */
    public CategoryView getCategory(UUID id) {
        return categoryQueryRepository.findById(id).orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND));
    }

// ============================== プライベートメソッド ==============================

    /**
     * 子カテゴリを木の節に変換する。
     *
     * @param parentId         親カテゴリ ID。最上位なら null
     * @param childrenByParent 親カテゴリ ID → 子カテゴリ
     * @return 木の節の一覧
     */
    private static List<CategoryNodeView> toNodes(@Nullable UUID parentId, Map<UUID, List<CategoryView>> childrenByParent) {
        return childrenByParent.getOrDefault(parentId, List.of()).stream()
                .map(category -> new CategoryNodeView(
                        category.id(),
                        category.name(),
                        category.depth(),
                        category.displayOrder(),
                        category.version(),
                        toNodes(category.id(), childrenByParent),
                        Category.canHaveChildren(category.depth())))
                .toList();
    }
}
