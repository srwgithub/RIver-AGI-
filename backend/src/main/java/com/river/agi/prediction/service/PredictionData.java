package com.river.agi.prediction.service;

import java.time.LocalDate;

public class PredictionData {
    
    public record SeriesPoint(LocalDate date, double value) {}
    
    public record PredictionPoint(String date, double predictedValue, 
                                  Double lowerBound, Double upperBound, 
                                  double confidence) {}
    
    public record AlgorithmResult(String algorithm, double mae, double rmse, 
                                   double mape, double r2) {}
}
