package kr.daboyeo.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

@Configuration
public class AsyncExecutionConfig {

    @Bean(name = "nearbyRefreshTaskExecutor")
    public TaskExecutor nearbyRefreshTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("nearby-refresh-");
        executor.setConcurrencyLimit(2);
        return executor;
    }

}
