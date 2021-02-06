package com.revents;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This event class annotation lets the event store
 * to put the event into the defined streams.
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface FixStream {

    /**
     * Name of the streams this event need to be bound to.
     *
     * @return all the target streams
     */
    String[] value();
}
