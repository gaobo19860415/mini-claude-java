package com.miniclaudecode.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configure Jackson to tolerate unknown properties from evolving APIs.
 */
@Configuration
public class JacksonConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer lenientDeserialization() {
        return builder -> builder.featuresToEnable(
                DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL
        ).featuresToDisable(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
        );
    }
}
