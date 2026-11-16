package io.github.asmeduardo.secureledger.shared;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Runs the {@code @Scheduled} jobs, such as the daily reconciliation. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfiguration {}
