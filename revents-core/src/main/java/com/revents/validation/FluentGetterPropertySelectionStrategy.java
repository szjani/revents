package com.revents.validation;

import org.hibernate.validator.spi.properties.ConstrainableExecutable;
import org.hibernate.validator.spi.properties.GetterPropertySelectionStrategy;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Uses the property name as method name to access the property for validation purposes.
 */
public class FluentGetterPropertySelectionStrategy implements GetterPropertySelectionStrategy {

    private final Set<String> methodNamesToIgnore;

    /**
     * Constructor.
     */
    public FluentGetterPropertySelectionStrategy() {
        // we will ignore all the method names coming from Object
        methodNamesToIgnore = Arrays.stream(Object.class.getDeclaredMethods())
            .map(Method::getName)
            .collect(Collectors.toSet());
    }

    @Override
    public Optional<String> getProperty(ConstrainableExecutable executable) {
        Optional<String> result = Optional.empty();
        if (!methodNamesToIgnore.contains(executable.getName())
            && executable.getReturnType() != void.class
            && executable.getParameterTypes().length <= 0) {
            result = Optional.of(executable.getName());
        }
        return result;
    }

    @Override
    public Set<String> getGetterMethodNameCandidates(String propertyName) {
        // As method name == property name, there always is just one possible name for a method
        return Collections.singleton(propertyName);
    }
}
