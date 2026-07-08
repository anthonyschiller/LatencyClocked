package com.ll.metrics.latency.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a method for latency timer descriptor generation. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface Timed {
  /**
   * Specifying this value overrides the default timer id. As a single-element annotation, this can
   * be declared without naming the annotation field at the application level.
   */
  String value() default "";
}
