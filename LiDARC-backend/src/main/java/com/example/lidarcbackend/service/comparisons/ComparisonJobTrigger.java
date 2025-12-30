package com.example.lidarcbackend.service.comparisons;

import com.example.lidarcbackend.api.comparison.dtos.PreProcessJobsReadyEvent;
import com.example.lidarcbackend.model.DTO.StartPreProcessJobDto;
import com.example.lidarcbackend.service.files.WorkerStartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ComparisonJobTrigger {
    private final WorkerStartService workerStartService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onComparisonJobsReady(PreProcessJobsReadyEvent event) {
        log.info("Transaction committed. Triggering {} preprocess worker jobs.", event.jobsToStart().size());

        for (StartPreProcessJobDto job : event.jobsToStart()) {
            workerStartService.startPreprocessingJob(job);
        }
    }
}
