package com.example.lidarcbackend.service.reports;

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

        reportComponentRegistry.put("HEATMAP", SimpleReportComponent::new);
        reportComponentRegistry.put("BOXPLOT", SimpleReportComponent::new);
        reportComponentRegistry.put("DISTRIBUTION", SimpleReportComponent::new);
        reportComponentRegistry.put("HISTO", SimpleReportComponent::new);
        reportComponentRegistry.put("SCATTER", SimpleReportComponent::new);
    }

    public IReportComponent getReportComponent(String type) {
        Supplier<IReportComponent> supplier = reportComponentRegistry.get(type);
        if (supplier == null) {
            //TODO: Exception
            return null;
        }
        return supplier.get();
    }

}
