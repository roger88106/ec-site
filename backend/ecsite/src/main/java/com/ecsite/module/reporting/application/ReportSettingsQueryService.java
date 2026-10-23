package com.ecsite.module.reporting.application;

import com.ecsite.module.reporting.application.model.view.ReportSettingsView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * レポートの設定の参照。設定の値の型は、presentation に出さずにここで view に変える。
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
