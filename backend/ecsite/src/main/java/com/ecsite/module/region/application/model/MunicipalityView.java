package com.ecsite.module.region.application.model;

import com.ecsite.module.region.domain.vo.Municipality;
import com.ecsite.module.region.domain.vo.Prefecture;

/**
 * 市区町村と、属する都道府県
 *
 * @param prefecture   都道府県
 * @param municipality 市区町村
 */
public record MunicipalityView(Prefecture prefecture, Municipality municipality) {
}
