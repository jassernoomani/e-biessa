package com.readdlytech.marketplaceapi.dto;

import java.util.List;

public record AssistantPartResult(PartResponse part, double score, List<String> matchReasons, String source) {}
