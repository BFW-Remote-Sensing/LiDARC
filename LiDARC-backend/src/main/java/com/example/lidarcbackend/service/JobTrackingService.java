package com.example.lidarcbackend.service;

import com.example.lidarcbackend.model.JobType;
import com.example.lidarcbackend.model.TrackedJob;
import com.example.lidarcbackend.model.entity.Comparison;
import com.example.lidarcbackend.model.entity.ComparisonFile;
import com.example.lidarcbackend.model.entity.File;
import com.example.lidarcbackend.model.entity.Folder;
import com.example.lidarcbackend.repository.ComparisonFileRepository;
import com.example.lidarcbackend.repository.ComparisonRepository;
import com.example.lidarcbackend.repository.FileRepository;
import com.example.lidarcbackend.repository.FolderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobTrackingService implements IJobTrackingService {

    private final Map<UUID, TrackedJob> jobs = new ConcurrentHashMap<>();

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final ComparisonFileRepository  comparisonFileRepository;
    private final ComparisonRepository  comparisonRepository;


    @Override
    public void registerJob(TrackedJob job) {
        jobs.put(job.getJobId(), job);
        log.info("Registered job: id={}, type={}, expiresAt={}",
                job.getJobId(),
                job.getJobType(),
                job.getTimeout());
    }

    @Override
    public void completeJob(UUID jobId) {
        TrackedJob removed = jobs.remove(jobId);
        if (removed != null) {
            log.info("Job completed and removed: id={}, type={}",
                    removed.getJobId(),
                    removed.getJobType());
        } else {
            log.warn("Attempted to complete job, but it was not found: id={}", jobId);
        }
    }

    @Override
    public Optional<TrackedJob> getJob(UUID jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void checkForTimeouts() {
        log.info("Checking for job timeouts...");
        Instant now = Instant.now();
        for(TrackedJob job : jobs.values()){
            if(job.isExpired(now)) {
                boolean removed = jobs.remove(job.getJobId(), job);
                if (!removed) {
                    continue;
                }
                log.warn("Job timed out and was removed: id={}, type={}, expiredAt={}",
                        job.getJobId(),
                        job.getJobType(),
                        job.getTimeout());
                setJobToFailed(job);
            }
        }
    }

    private void setJobToFailed(TrackedJob job) {
        if(job.getJobType() == JobType.METADATA) {
            setMetadataToFailed(job);
        } else  if(job.getJobType() == JobType.PREPROCESSING) {
            setComparisonFileToFailed(job);
        } else if (job.getJobType() == JobType.COMPARISON) {
            setComparisonToFailed(job);
        }
    }

    private void setMetadataToFailed(TrackedJob job) {
        fileRepository.findById(job.getPayload().get("fileId")).ifPresent(file -> {
            if (file.getStatus() == File.FileStatus.PROCESSING) {
                file.setStatus(File.FileStatus.FAILED);
                file.setErrorMsg("Metadata job timed out");
                fileRepository.save(file);
                tryUpdateFolderStatusToFailed(file);
            }
        });
    }

    private void tryUpdateFolderStatusToFailed(File file) {
        if (file == null || file.getFolder() == null) return;

        Folder folder = file.getFolder();

        if (folder.getFiles() == null || folder.getFiles().isEmpty()) {
            return;
        }
        folder.setStatus("FAILED");
        folderRepository.save(folder);
    }

    private void setComparisonFileToFailed(TrackedJob job) {
        Long comparisonId = job.getPayload().get("comparisonId");
        Long fileId = job.getPayload().get("fileId");
        if(fileId == null || comparisonId == null) return;
        Optional<ComparisonFile> cfOpt = comparisonFileRepository.findComparisonFiles(comparisonId, fileId);
        if (cfOpt.isEmpty()) return;

        ComparisonFile cf = cfOpt.get();
        if (cf.getStatus() == ComparisonFile.Status.PREPROCESSING) {
            cf.setStatus(ComparisonFile.Status.FAILED);
            cf.setErrorMsg("Preprocessing timed out");
            comparisonFileRepository.save(cf);
            Optional<Comparison> comparisonOpt = comparisonRepository.findComparisonsById(comparisonId);
            if (comparisonOpt.isPresent() && comparisonOpt.get().getStatus() != Comparison.Status.FAILED) {
                Comparison comparison = comparisonOpt.get();
                comparison.setStatus(Comparison.Status.FAILED);
                comparison.setErrorMessage("One or more preprocessing files timed out");
                comparisonRepository.save(comparison);
            }
        }
    }

    private void setComparisonToFailed(TrackedJob job) {
        Long comparisonId =  job.getPayload().get("comparisonId");
        if(comparisonId == null) return;
        Optional<Comparison> comparisonOpt = comparisonRepository.findComparisonsById(comparisonId);
        if (comparisonOpt.isEmpty()) return;
        if (comparisonOpt.get().getStatus() != Comparison.Status.FAILED) {
            Comparison comparison = comparisonOpt.get();
            comparison.setStatus(Comparison.Status.FAILED);
            comparison.setErrorMessage("Comparison job timed out");
            comparisonRepository.save(comparison);
        }
    }

}
