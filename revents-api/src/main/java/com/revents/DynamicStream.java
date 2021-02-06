package com.revents;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This event method annotation lets the event store
 * to put the event into the defined streams.
 *
 * <p>
 *     The event are bound to streams having name prefixed with any of the {@code prefix},
 *     followed by the String representation of the result of the annotated method.
 * </p>
 *
 * <p>
 *     In this example the {@code Order} instance will be bound to stream "order-123";
 * <pre>
 *     class Order {
 *
 *         &#64;DynamicStream(prefix = "order-")
 *         String orderId() {
 *             return "123";
 *         }
 *     }
 * </pre>
 * </p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface DynamicStream {

    /**
     * Prefixes of the expected stream names.
     *
     * @return the prefixes
     */
    String[] prefix() default {""};
}
