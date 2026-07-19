package com.cron.service;

import com.cron.repository.JobRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * job_runs grows without bound, so the limit clamp is the only thing stopping
 * a single request from pulling the whole table into memory.
 */
class JobRunServiceTest {

    private JobRunRepository repository;
    private JobRunService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(JobRunRepository.class);
        when(repository.findAllByOrderByStartedAtDesc(any())).thenReturn(List.of());
        when(repository.findByJobIdOrderByStartedAtDesc(any(), any())).thenReturn(List.of());
        service = new JobRunService(repository);
    }

    private int capturedPageSize() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAllByOrderByStartedAtDesc(pageable.capture());
        return pageable.getValue().getPageSize();
    }

    @Test
    void appliesDefaultLimitWhenUnspecified() {
        service.findRecent(null);

        assertThat(capturedPageSize()).isEqualTo(JobRunService.DEFAULT_LIMIT);
    }

    @Test
    void honoursAReasonableLimit()  {
        service.findRecent(10);

        assertThat(capturedPageSize()).isEqualTo(10);
    }

    @Test
    void clampsExcessiveLimit() {
        service.findRecent(100_000);

        assertThat(capturedPageSize()).isEqualTo(JobRunService.MAX_LIMIT);
    }

    @Test
    void fallsBackToDefaultForNonsenseLimits() {
        service.findRecent(0);

        assertThat(capturedPageSize()).isEqualTo(JobRunService.DEFAULT_LIMIT);
    }

    @Test
    void clampsPerJobQueriesToo() {
        service.findRecentForJob(1L, 100_000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByJobIdOrderByStartedAtDesc(any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(JobRunService.MAX_LIMIT);
    }
}
