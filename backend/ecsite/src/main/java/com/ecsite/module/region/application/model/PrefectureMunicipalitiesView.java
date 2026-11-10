package com.ecsite.module.region.application.model;

import com.ecsite.module.region.domain.vo.Municipality;
import com.ecsite.module.region.domain.vo.Prefecture;

import java.util.List;

/**
 * 都道府県と、その市区町村の一覧
 *
 * @param prefecture     都道府県
 * @param municipalities コードの順の市区町村
 */
public record PrefectureMunicipalitiesView(Prefecture prefecture, List<Municipality> municipalities) {
}
