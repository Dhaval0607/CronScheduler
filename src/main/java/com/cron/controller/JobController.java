package com.cron.controller;

import com.cron.dto.CronJobRequest;
import com.cron.dto.CronJobResponse;
import com.cron.dto.JobRunResponse;
import com.cron.service.CronJobService;
import com.cron.service.JobRunService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final CronJobService cronJobService;
    private final JobRunService jobRunService;

    public JobController(CronJobService cronJobService, JobRunService jobRunService) {
        this.cronJobService = cronJobService;
        this.jobRunService = jobRunService;
    }

    @PostMapping
    public ResponseEntity<CronJobResponse> create(@Valid @RequestBody CronJobRequest request) {
        CronJobResponse response = cronJobService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<CronJobResponse>> findAll() {
        return ResponseEntity.ok(cronJobService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CronJobResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(cronJobService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CronJobResponse> update(@PathVariable Long id, @Valid @RequestBody CronJobRequest request) {
        return ResponseEntity.ok(cronJobService.update(id, request));
    }

    /** History survives job deletion, so this returns rows even for a deleted id. */
    @GetMapping("/{id}/runs")
    public ResponseEntity<List<JobRunResponse>> findRuns(@PathVariable Long id,
                                                        @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(jobRunService.findRecentForJob(id, limit));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        cronJobService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
