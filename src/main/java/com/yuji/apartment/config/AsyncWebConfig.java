package com.yuji.apartment.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AsyncWebConfig implements WebMvcConfigurer {
    @Bean public ThreadPoolTaskExecutor exportExecutor(){
        var executor=new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("appointment-export-");
        executor.setCorePoolSize(2);executor.setMaxPoolSize(4);executor.setQueueCapacity(20);
        executor.setWaitForTasksToCompleteOnShutdown(true);executor.setAwaitTerminationSeconds(10);
        executor.setTaskDecorator(task->{
            var captured=MDC.getCopyOfContextMap();
            return ()->{var previous=MDC.getCopyOfContextMap();
                try{if(captured==null)MDC.clear();else MDC.setContextMap(captured);task.run();}
                finally{if(previous==null)MDC.clear();else MDC.setContextMap(previous);}
            };
        });
        return executor;
    }
    @Override public void configureAsyncSupport(AsyncSupportConfigurer configurer){
        configurer.setTaskExecutor(exportExecutor());configurer.setDefaultTimeout(30_000);
    }
}
