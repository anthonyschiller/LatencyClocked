package com.ll.metrics.latency.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a concrete method for build-time latency instrumentation.
 *
 * <p>This annotation is intentionally a marker. It has no configurable value, alias, or display
 * name. The latency-clocked Maven plugin derives the canonical method identity from the annotated
 * method itself and injects timing bytecode during the build.
 *
 * <p>Only complete successful executions are recorded. Methods that exit by throwing an exception
 * are intentionally excluded from latency recording.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface Timed {}
