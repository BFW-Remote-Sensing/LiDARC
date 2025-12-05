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
        reportComponentRegistry.put("simple", SimpleReportComponent::new);
    }

    public IReportComponent getReportComponent(String type) {
        Supplier<IReportComponent> supplier =  reportComponentRegistry.get(type);
        if (supplier == null) {
            //TODO: Exception
            return null;
        }
        return supplier.get();
    }

}
