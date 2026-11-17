package com.ecsite.module.reporting.application;

import com.ecsite.module.reporting.application.model.view.ReportSettingsView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * レポートの設定のクエリサービス
 */
@Service
@RequiredArgsConstructor
public class ReportSettingsQueryService {

    /**
     * レポートの設定
     */
    private final ReportingProperties properties;

    /**
     * レポートの設定を取得する。
     *
     * @return レポートの設定
     */
    public ReportSettingsView settings() {
        return new ReportSettingsView(properties.fiscalYearStartMonth());
    }
}
