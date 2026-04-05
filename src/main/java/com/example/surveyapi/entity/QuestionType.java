package com.example.surveyapi.entity;

public enum QuestionType {

    SINGLE_CHOICE,
    MULTI_CHOICE,
    TEXT,
    RATING;

    public boolean isChoiceType() {
        return this == SINGLE_CHOICE || this == MULTI_CHOICE;
    }
}
