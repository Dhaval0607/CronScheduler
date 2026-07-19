package com.cron.service;

import com.cron.dto.CronJobRequest;
import com.cron.dto.CronJobResponse;
import com.cron.exception.DuplicateCronJobNameException;
import com.cron.exception.InvalidCronExpressionException;
import com.cron.executor.JobExecutor;
import com.cron.executor.JobExecutorRegistry;
import com.cron.model.CronJob;
import com.cron.model.JobType;
import com.cron.repository.CronJobRepository;
import com.cron.scheduler.NextRunCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class CronJobServiceTest {

    private CronJobRepository repository;
    private JobExecutorRegistry registry;
    private CronJobService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(CronJobRepository.class);
        registry = Mockito.mock(JobExecutorRegistry.class);
        when(registry.forType(any())).thenReturn(Mockito.mock(JobExecutor.class));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service = new CronJobService(repository, new NextRunCalculator(), registry);
    }

    private CronJobRequest request(String cron, Boolean enabled) {
        return new CronJobRequest("job", "desc", cron, JobType.EMAIL, null, enabled);
    }

    @Test
    void schedulesNextRunOnCreate() {
        CronJobResponse response = service.create(request("0 0 * * * *", true));

        assertThat(response.nextRunAt()).isNotNull();
        assertThat(response.lastRunAt()).isNull();
    }

    @Test
    void enabledDefaultsToTrueWhenOmitted() {
        CronJobResponse response = service.create(request("0 0 * * * *", null));

        assertThat(response.enabled()).isTrue();
        assertThat(response.nextRunAt()).isNotNull();
    }

    @Test
    void disabledJobsGetNoNextRun() {
        // This is what keeps them out of the due query entirely.
        CronJobResponse response = service.create(request("0 0 * * * *", false));

        assertThat(response.enabled()).isFalse();
        assertThat(response.nextRunAt()).isNull();
    }

    @Test
    void rejectsInvalidCronExpression() {
        assertThatThrownBy(() -> service.create(request("not a cron", true)))
                .isInstanceOf(InvalidCronExpressionException.class);
    }

    @Test
    void rejectsFiveFieldCrontabSyntax() {
        assertThatThrownBy(() -> service.create(request("0 9 * * *", true)))
                .isInstanceOf(InvalidCronExpressionException.class);
    }

    @Test
    void rejectsDuplicateNameOnCreate() {
        when(repository.existsByNameIgnoreCase(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("0 0 * * * *", true)))
                .isInstanceOf(DuplicateCronJobNameException.class);
    }

    @Test
    void allowsAJobToKeepItsOwnNameOnUpdate() {
        CronJob existing = new CronJob();
        existing.setId(1L);
        existing.setName("job");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.findByNameIgnoreCase("job")).thenReturn(Optional.of(existing));

        CronJobResponse response = service.update(1L, request("0 0 * * * *", true));

        assertThat(response.name()).isEqualTo("job");
    }

    @Test
    void rejectsRenamingOntoAnotherJobsName() {
        CronJob target = new CronJob();
        target.setId(1L);
        CronJob other = new CronJob();
        other.setId(2L);
        when(repository.findById(1L)).thenReturn(Optional.of(target));
        when(repository.findByNameIgnoreCase("job")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.update(1L, request("0 0 * * * *", true)))
                .isInstanceOf(DuplicateCronJobNameException.class);
    }

    @Test
    void updateRecomputesTheSchedule() {
        CronJob existing = new CronJob();
        existing.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());

        CronJobResponse response = service.update(1L, request("0 0 * * * *", true));

        assertThat(response.nextRunAt()).isNotNull();
    }
}
