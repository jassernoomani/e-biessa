package com.readdlytech.marketplaceapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Profile("ha")
@EnableScheduling
public class HaRealtimeConfig {}
