package com.readdlytech.marketplaceapi.dto;

import java.util.List;

public record ConversationDetailResponse(ConversationResponse conversation, List<MessageResponse> messages) {}
