package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.config.HaRealtimeConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

import static org.junit.jupiter.api.Assertions.*;

class HaProfileTests {
    @Test
    void schedulingIsEnabledOnlyForHaProfile() {
        for (String profile : new String[]{"ha", "test"}) {
            try (var context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().setActiveProfiles(profile);
                context.register(HaRealtimeConfig.class);
                context.refresh();
                assertEquals(profile.equals("ha"), context.containsBean(
                        TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME));
                assertEquals(profile.equals("ha"), !context.getBeansOfType(HaRealtimeConfig.class).isEmpty());
            }
        }
    }
}
