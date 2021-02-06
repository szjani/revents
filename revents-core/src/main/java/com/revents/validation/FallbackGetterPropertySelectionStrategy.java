package com.revents.validation;

import com.google.common.collect.Sets;
import org.hibernate.validator.internal.properties.DefaultGetterPropertySelectionStrategy;
import org.hibernate.validator.spi.properties.ConstrainableExecutable;
import org.hibernate.validator.spi.properties.GetterPropertySelectionStrategy;

import java.util.Optional;
import java.util.Set;

/**
 * Get the property values based on different method names.
 */
public class FallbackGetterPropertySelectionStrategy implements GetterPropertySelectionStrategy {

    private final GetterPropertySelectionStrategy fallbackStrategy = new DefaultGetterPropertySelectionStrategy();
    private final GetterPropertySelectionStrategy defaultStrategy = new FluentGetterPropertySelectionStrategy();

    @Override
    public Optional<String> getProperty(ConstrainableExecutable executable) {
        return defaultStrategy.getProperty(executable)
            .or(() -> fallbackStrategy.getProperty(executable));
    }

    @Override
    public Set<String> getGetterMethodNameCandidates(String propertyName) {
        return Sets.union(
            defaultStrategy.getGetterMethodNameCandidates(propertyName),
            fallbackStrategy.getGetterMethodNameCandidates(propertyName));
    }
}
