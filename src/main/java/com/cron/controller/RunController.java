package com.cron.controller;

import com.cron.dto.JobRunResponse;
import com.cron.service.JobRunService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Run history lives at its own root rather than under /jobs/runs, which would
 * collide with the /jobs/{id} pattern.
 */
@RestController
@RequestMapping("/api/v1/runs")
public class RunController {

    private final JobRunService jobRunService;

    public RunController(JobRunService jobRunService) {
        this.jobRunService = jobRunService;
    }

    @GetMapping
    public ResponseEntity<List<JobRunResponse>> findRecent(@RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(jobRunService.findRecent(limit));
    }
}
