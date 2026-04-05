package com.example.surveyapi.dto.response;

public record OptionCountResponse(
        Long optionId,
        String optionContent,
        Long count
) {}
