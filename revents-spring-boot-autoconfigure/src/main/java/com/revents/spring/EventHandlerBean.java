package com.revents.spring;

import com.revents.EventProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Component
@Qualifier(EventProcessor.EventProcessorId.DEFAULT_NAME)
public @interface EventHandlerBean {

    /**
     * Identifies the {@link com.revents.EventProcessor} that
     * manages this event handler.
     *
     * @return the event processor ID
     */
    String processorId() default EventProcessor.EventProcessorId.DEFAULT_NAME;
}
