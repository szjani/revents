package com.revents.serialization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SerializedType {

    /**
     * Refer the annotated class as the given string.
     *
     * @return the identifier of the annotated type
     */
    String asString() default "";

    /**
     * Refer the annotated class as the given parent (or self) class.
     *
     * @return the parent class as identifier
     */
    Class<?> asSuper() default void.class;
}
