package com.yuji.apartment.monitor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class MethodTimingAspect {

    private static final Logger log =
            LoggerFactory.getLogger(MethodTimingAspect.class);

    @Around("@annotation(com.yuji.apartment.monitor.TimedOperation)")
    public Object measure(
            ProceedingJoinPoint joinPoint
    ) throws Throwable {

        long started = System.nanoTime();

        try {
            return joinPoint.proceed();
        } finally {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - started
            );

            log.info(
                    "方法执行结束 method={} elapsedMs={}",
                    joinPoint.getSignature().toShortString(),
                    elapsedMs
            );
        }
    }
}