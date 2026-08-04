package com.river.agi.trend.controller;

import com.river.agi.common.ApiResponse;
import com.river.agi.prediction.entity.PredictionTask;
import com.river.agi.prediction.mapper.PredictionTaskMapper;
import com.river.agi.trend.entity.AnomalyAlert;
import com.river.agi.trend.entity.DecisionScenario;
import com.river.agi.trend.entity.RootCauseAnalysis;
import com.river.agi.trend.entity.TrendDiagnosis;
import com.river.agi.trend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping({"/api/v1/trend", "/v1/trend"})
@RequiredArgsConstructor
public class TrendAnalysisController {

    private final TrendDiagnosisService trendDiagnosisService;
    private final ComparisonAnalysisService comparisonService;
    private final AnomalyDetectionService anomalyService;
    private final RootCauseAnalysisService rcaService;
    private final OlapAnalysisService olapService;
    private final DecisionSupportService decisionService;

    @Autowired(required = false)
    private PredictionTaskMapper predictionTaskMapper;

    @PostMapping("/diagnosis/{predictionTaskId}")
    public ApiResponse<TrendDiagnosis> diagnose(@PathVariable Long predictionTaskId) {
        return ApiResponse.ok(trendDiagnosisService.diagnose(predictionTaskId));
    }

    @GetMapping("/diagnosis/{predictionTaskId}/latest")
    public ApiResponse<TrendDiagnosis> getLatestDiagnosis(@PathVariable Long predictionTaskId) {
        return ApiResponse.ok(trendDiagnosisService.getLatestDiagnosis(predictionTaskId));
    }

    @GetMapping("/comparison/actual-vs-predicted/{predictionTaskId}")
    public ApiResponse<Map<String, Object>> actualVsPredicted(@PathVariable Long predictionTaskId) {
        return ApiResponse.ok(comparisonService.getActualVsPredicted(predictionTaskId));
    }

    @GetMapping("/comparison/yoy")
    public ApiResponse<Map<String, Object>> yearOverYear(
            @RequestParam Long datasetId,
            @RequestParam String timeField,
            @RequestParam String targetField,
            @RequestParam(required = false) String currentPeriod) {
        return ApiResponse.ok(comparisonService.getYearOverYear(datasetId, timeField, targetField, currentPeriod));
    }

    @GetMapping("/comparison/mom")
    public ApiResponse<Map<String, Object>> monthOverMonth(
            @RequestParam Long datasetId,
            @RequestParam String timeField,
            @RequestParam String targetField) {
        return ApiResponse.ok(comparisonService.getMonthOverMonth(datasetId, timeField, targetField));
    }

    @GetMapping("/comparison/multi-algorithm")
    public ApiResponse<Map<String, Object>> multiAlgorithmComparison(
            @RequestParam Long datasetId,
            @RequestParam String timeField,
            @RequestParam String targetField,
            @RequestParam(defaultValue = "30") int forecastDays) {
        return ApiResponse.ok(comparisonService.getMultiAlgorithmComparison(datasetId, timeField, targetField, forecastDays));
    }

    @PostMapping("/anomaly/detect-deviations/{predictionTaskId}")
    public ApiResponse<List<AnomalyAlert>> detectDeviations(@PathVariable Long predictionTaskId) {
        return ApiResponse.ok(anomalyService.detectPredictionDeviations(predictionTaskId));
    }

    @PostMapping("/anomaly/detect-deviations")
    public ApiResponse<List<AnomalyAlert>> detectDeviationsByBody(@RequestBody Map<String, Object> body) {
        Long taskId = null;
        if (body.get("predictionTaskId") != null) {
            taskId = Long.valueOf(body.get("predictionTaskId").toString());
        } else if (body.get("taskId") != null) {
            taskId = Long.valueOf(body.get("taskId").toString());
        }
        return ApiResponse.ok(anomalyService.detectPredictionDeviations(taskId));
    }

    @GetMapping("/anomaly/change-points")
    public ApiResponse<List<Map<String, Object>>> changePoints(
            @RequestParam Long datasetId,
            @RequestParam String timeField,
            @RequestParam String targetField) {
        return ApiResponse.ok(anomalyService.detectChangePoints(datasetId, timeField, targetField));
    }

    @GetMapping("/anomaly/volatility")
    public ApiResponse<List<Map<String, Object>>> volatilityAnomalies(
            @RequestParam Long datasetId,
            @RequestParam String timeField,
            @RequestParam String targetField) {
        return ApiResponse.ok(anomalyService.detectVolatilityAnomalies(datasetId, timeField, targetField));
    }

    @GetMapping("/anomaly/alerts")
    public ApiResponse<List<AnomalyAlert>> getAlerts(
            @RequestParam(required = false) Long datasetId,
            @RequestParam(required = false) Long predictionTaskId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(anomalyService.getAlerts(datasetId, predictionTaskId, severity, status));
    }

    @GetMapping("/anomaly/summary")
    public ApiResponse<Map<String, Object>> getAlertSummary(@RequestParam Long datasetId) {
        return ApiResponse.ok(anomalyService.getAlertSummary(datasetId));
    }

    @PostMapping("/anomaly/{alertId}/resolve")
    public ApiResponse<AnomalyAlert> resolveAlert(@PathVariable Long alertId, @RequestBody(required = false) Map<String, String> body) {
        String resolution = body != null ? body.getOrDefault("resolution", "") : "";
        return ApiResponse.ok(anomalyService.resolveAlert(alertId, resolution));
    }

    @PostMapping("/rca/{alertId}")
    public ApiResponse<RootCauseAnalysis> analyzeRootCause(@PathVariable Long alertId) {
        return ApiResponse.ok(rcaService.analyze(alertId));
    }

    @GetMapping("/rca/{id}")
    public ApiResponse<RootCauseAnalysis> getRca(@PathVariable Long id) {
        return ApiResponse.ok(rcaService.getById(id));
    }

    @GetMapping("/rca/drill-down")
    public ApiResponse<Map<String, Object>> drillDown(
            @RequestParam Long datasetId,
            @RequestParam String dimension,
            @RequestParam(required = false) String dimensionValue,
            @RequestParam String targetField,
            @RequestParam(required = false) String timeField,
            @RequestParam(required = false) String dateRange) {
        return ApiResponse.ok(rcaService.drillDown(datasetId, dimension, dimensionValue, targetField, timeField, dateRange));
    }

    @GetMapping("/rca/contribution")
    public ApiResponse<List<Map<String, Object>>> contributionBreakdown(
            @RequestParam Long datasetId,
            @RequestParam String targetField,
            @RequestParam(required = false) String timeField,
            @RequestParam(defaultValue = "10") String topN) {
        return ApiResponse.ok(rcaService.getContributionBreakdown(datasetId, targetField, timeField, topN));
    }

    @PostMapping("/olap/pivot")
    public ApiResponse<Map<String, Object>> pivotTable(@RequestBody Map<String, Object> params) {
        Long datasetId = null;
        String rowDim = null;
        String colDim = null;
        String valueField = null;
        String aggFunc = "SUM";

        if (params.get("datasetId") != null) {
            datasetId = Long.valueOf(params.get("datasetId").toString());
        } else if (params.get("taskId") != null && predictionTaskMapper != null) {
            Long taskId = Long.valueOf(params.get("taskId").toString());
            PredictionTask task = predictionTaskMapper.selectById(taskId);
            if (task != null) {
                datasetId = task.getDatasetId();
            }
        }

        if (params.get("rowDim") != null) {
            rowDim = params.get("rowDim").toString();
        }
        if (params.get("colDim") != null) {
            colDim = params.get("colDim").toString();
        }
        if (params.get("valueField") != null) {
            valueField = params.get("valueField").toString();
        }

        if (params.get("aggFunc") != null) {
            aggFunc = params.get("aggFunc").toString();
        } else if (params.get("aggregation") != null) {
            aggFunc = params.get("aggregation").toString();
        }

        if (params.get("dimensions") != null) {
            @SuppressWarnings("unchecked")
            List<String> dimensions = (List<String>) params.get("dimensions");
            if (dimensions != null && dimensions.size() >= 1 && rowDim == null) {
                rowDim = dimensions.get(0);
            }
            if (dimensions != null && dimensions.size() >= 2 && colDim == null) {
                colDim = dimensions.get(1);
            }
        }

        if (params.get("metrics") != null) {
            @SuppressWarnings("unchecked")
            List<String> metrics = (List<String>) params.get("metrics");
            if (metrics != null && !metrics.isEmpty() && valueField == null) {
                valueField = metrics.get(0);
            }
        }

        if (datasetId == null) {
            throw new IllegalArgumentException("datasetId or taskId is required");
        }

        return ApiResponse.ok(olapService.pivotTable(datasetId, rowDim, colDim, valueField, aggFunc));
    }

    @PostMapping("/olap/slice")
    public ApiResponse<Map<String, Object>> sliceAndDice(@RequestBody Map<String, Object> params) {
        return ApiResponse.ok(olapService.sliceAndDice(
                Long.valueOf(params.get("datasetId").toString()),
                params.get("dimensions").toString(),
                params.get("measures").toString(),
                params.getOrDefault("filters", "").toString(),
                params.getOrDefault("timeField", null) != null ? params.get("timeField").toString() : null
        ));
    }

    @GetMapping("/olap/period-comparison")
    public ApiResponse<Map<String, Object>> periodComparison(
            @RequestParam Long datasetId,
            @RequestParam String timeField,
            @RequestParam String measure,
            @RequestParam String currentStart,
            @RequestParam String currentEnd,
            @RequestParam(defaultValue = "MOM") String compareType) {
        return ApiResponse.ok(olapService.getPeriodComparison(datasetId, timeField, measure, currentStart, currentEnd, compareType));
    }

    @GetMapping("/olap/kpi")
    public ApiResponse<Map<String, Object>> kpiDashboard(
            @RequestParam Long datasetId,
            @RequestParam String measure,
            @RequestParam(required = false) String timeField) {
        return ApiResponse.ok(olapService.getKpiDashboard(datasetId, measure, timeField));
    }

    @PostMapping("/decision/what-if")
    public ApiResponse<Object> whatIfScenario(@RequestBody Map<String, Object> params, Authentication auth) {
        Long predictionTaskId = null;
        if (params.get("predictionTaskId") != null) {
            predictionTaskId = Long.valueOf(params.get("predictionTaskId").toString());
        } else if (params.get("taskId") != null) {
            predictionTaskId = Long.valueOf(params.get("taskId").toString());
        }

        boolean isSimpleFormat = params.containsKey("growthFactor") || params.containsKey("seasonFactor")
                || params.containsKey("shockType") || params.containsKey("forecastDays");

        if (isSimpleFormat) {
            Map<String, Object> threeScenarios = decisionService.generateThreeScenarios(predictionTaskId);

            Double growthFactor = params.get("growthFactor") != null ?
                    Double.parseDouble(params.get("growthFactor").toString()) : null;
            Double seasonFactor = params.get("seasonFactor") != null ?
                    Double.parseDouble(params.get("seasonFactor").toString()) : null;
            String shockType = params.get("shockType") != null ?
                    params.get("shockType").toString() : "MEDIUM";

            Map<String, Double> baseFactors = new LinkedHashMap<>();
            if (growthFactor != null) baseFactors.put("growth", growthFactor);
            if (seasonFactor != null) baseFactors.put("seasonality", seasonFactor);
            if ("POSITIVE".equalsIgnoreCase(shockType)) baseFactors.put("shock", 0.1);
            else if ("NEGATIVE".equalsIgnoreCase(shockType)) baseFactors.put("shock", -0.1);
            else baseFactors.put("shock", 0.0);

            String scenarioName = params.containsKey("scenarioName") ?
                    params.get("scenarioName").toString() : "自定义What-If分析";
            String assumptions = params.containsKey("assumptions") ?
                    params.get("assumptions").toString() :
                    String.format("增长因子: %.2f, 季节因子: %.2f, 冲击类型: %s",
                            growthFactor != null ? growthFactor : 0,
                            seasonFactor != null ? seasonFactor : 0,
                            shockType);

            DecisionScenario customScenario = decisionService.createWhatIfScenario(
                    predictionTaskId, scenarioName, baseFactors, assumptions);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("scenarios", threeScenarios);
            result.put("customScenario", customScenario);
            return ApiResponse.ok(result);
        } else {
            @SuppressWarnings("unchecked")
            Map<String, Double> factors = (Map<String, Double>) params.getOrDefault("adjustedFactors", Map.of());
            return ApiResponse.ok(decisionService.createWhatIfScenario(
                    predictionTaskId,
                    params.getOrDefault("scenarioName", "What-If分析").toString(),
                    factors,
                    params.getOrDefault("assumptions", "").toString()
            ));
        }
    }

    @GetMapping("/decision/scenarios/{predictionTaskId}")
    public ApiResponse<List<DecisionScenario>> getScenarios(@PathVariable Long predictionTaskId) {
        return ApiResponse.ok(decisionService.getScenarios(predictionTaskId));
    }

    @GetMapping("/decision/three-scenarios/{predictionTaskId}")
    public ApiResponse<Map<String, Object>> threeScenarios(@PathVariable Long predictionTaskId) {
        return ApiResponse.ok(decisionService.generateThreeScenarios(predictionTaskId));
    }

    @GetMapping("/decision/recommendations")
    public ApiResponse<Map<String, Object>> decisionRecommendations(
            @RequestParam Long datasetId,
            @RequestParam String targetField) {
        return ApiResponse.ok(decisionService.getDecisionRecommendations(datasetId, targetField));
    }
}
