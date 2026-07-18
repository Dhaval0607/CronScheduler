package com.cron.cronscheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.cron")
@EntityScan("com.cron.model")
@EnableJpaRepositories("com.cron.repository")
@EnableScheduling
public class CronSchedulerApplication {

	public static void main(String[] args) {
		SpringApplication.run(CronSchedulerApplication.class, args);
	}

}
