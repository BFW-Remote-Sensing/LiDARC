package com.example.lidarcbackend.service.reports;

import com.example.lidarcbackend.service.reports.component.HeatmapReportComponent;
import com.example.lidarcbackend.service.reports.component.SideBySideHeatmapComponent;
import com.example.lidarcbackend.service.reports.component.SimpleReportComponent;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class ReportComponentFactory {
    Map<String, Supplier<IReportComponent>> reportComponentRegistry;

    public ReportComponentFactory() {
        reportComponentRegistry = new HashMap<>();
        reportComponentRegistry.put("SIMPLE", SimpleReportComponent::new);
        reportComponentRegistry.put("HEATMAP", HeatmapReportComponent::new);
        reportComponentRegistry.put("BOXPLOT", SimpleReportComponent::new);
        reportComponentRegistry.put("DISTRIBUTION", SimpleReportComponent::new);
        reportComponentRegistry.put("DISTRIBUTION_DIFF", SimpleReportComponent::new);
        reportComponentRegistry.put("HISTO", SimpleReportComponent::new);
        reportComponentRegistry.put("SCATTER", SimpleReportComponent::new);
        reportComponentRegistry.put("SIDE_BY_SIDE", SideBySideHeatmapComponent::new);
    }

    public IReportComponent getReportComponent(String type) {
        String normalizedType = type != null ? type.toUpperCase() : "";
        Supplier<IReportComponent> supplier = reportComponentRegistry.get(normalizedType);
        if (supplier == null) {
            //TODO: Exception
            return null;
        }
        return supplier.get();
    }

}
