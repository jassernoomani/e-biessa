package com.readdlytech.marketplaceapi.dto;

import java.util.List;

public record SemanticPartResult(PartResponse part, double score, List<String> matchReasons) {}
