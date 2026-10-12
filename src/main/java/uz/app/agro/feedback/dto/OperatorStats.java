package uz.app.agro.feedback.dto;

public record OperatorStats(Long staffId, String email, long open, long closed, long rated, Double avgRating) {}
