package com.river.agi.module;

import com.river.agi.common.ApiResponse;
import com.river.agi.module.dto.ModuleActionExecuteResponse;
import com.river.agi.security.service.SecurityService;
import com.river.agi.collection.service.CollectionTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/v1/module-actions", "/v1/module-actions"})
public class ModuleActionController {

    private static final List<Map<String, Object>> ACTION_LOG = new ArrayList<>();

    @Autowired(required = false)
    private SecurityService securityService;

    @Autowired(required = false)
    private CollectionTaskService collectionTaskService;

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(@RequestParam(defaultValue = "dashboard") String module) {
        List<Map<String, Object>> lastActions = ACTION_LOG.stream()
                .filter(item -> module.equals(item.get("module")))
                .collect(Collectors.toList());
        int fromIndex = Math.max(0, lastActions.size() - 5);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("module", module);
        data.put("title", title(module));
        data.put("description", description(module));
        data.put("steps", steps(module));
        data.put("lastActions", lastActions.subList(fromIndex, lastActions.size()));
        return ApiResponse.ok(data);
    }

    @PostMapping("/execute")
    public ApiResponse<ModuleActionExecuteResponse> execute(@RequestBody Map<String, Object> request,
                                                            Authentication authentication) {
        String module = String.valueOf(request.getOrDefault("module", "dashboard"));
        String action = String.valueOf(request.getOrDefault("action", "页面操作"));
        String actionType = resolveActionType(module, action);

        ModuleActionExecuteResponse result = new ModuleActionExecuteResponse();
        result.setModule(module);
        result.setAction(action);
        result.setFinishedAt(LocalDateTime.now().toString());
        result.setActionType(actionType);

        Object realResult = null;
        boolean executedRealAction = false;

        try {
            if ("SECURITY_SCAN".equals(actionType) && securityService != null) {
                Object datasetIdObj = request.get("datasetId");
                if (datasetIdObj != null) {
                    Long datasetId = Long.valueOf(datasetIdObj.toString());
                    realResult = securityService.scanSensitiveData(datasetId, authentication);
                    executedRealAction = true;
                }
            } else if ("DATA_CLEAN".equals(actionType) && collectionTaskService != null) {
                Object taskIdObj = request.get("taskId");
                if (taskIdObj != null) {
                    Long taskId = Long.valueOf(taskIdObj.toString());
                    @SuppressWarnings("unchecked")
                    Map<String, Object> config = (Map<String, Object>) request.getOrDefault("config", Map.of(
                            "removeEmpty", true,
                            "removeDuplicate", true,
                            "validateFormat", true
                    ));
                    realResult = collectionTaskService.cleanPreview(taskId, config);
                    executedRealAction = true;
                }
            }
        } catch (Exception e) {
            result.setStatus("FAILED");
            result.setMessage("操作执行失败: " + e.getMessage());
        }

        if (executedRealAction) {
            result.setStatus("COMPLETED");
            result.setMessage("操作执行成功");
            result.setResult(realResult);
            result.setNextStep("操作已完成，可查看执行结果");
        } else {
            result.setStatus("REDIRECT");
            result.setRedirect(resolveRedirect(module, action));
            result.setMessage(resolveActionMessage(module, action));
            result.setNextStep(resolveNextStep(module, action));
        }

        Map<String, Object> logEntry = new LinkedHashMap<>();
        logEntry.put("module", module);
        logEntry.put("action", action);
        logEntry.put("actionType", actionType);
        logEntry.put("status", result.getStatus());
        logEntry.put("message", result.getMessage());
        logEntry.put("finishedAt", result.getFinishedAt());
        logEntry.put("redirect", result.getRedirect());
        ACTION_LOG.add(logEntry);
        if (ACTION_LOG.size() > 200) {
            ACTION_LOG.remove(0);
        }

        return ApiResponse.ok(result);
    }

    private String resolveRedirect(String module, String action) {
        return switch (module) {
            case "annotation-platform" -> {
                if (action.contains("导入") || action.contains("上传") || action.contains("清洗")) {
                    yield "/collection-annotation";
                } else if (action.contains("标签") || action.contains("标注任务")) {
                    yield "/annotation-platform";
                }
                yield "/collection-annotation";
            }
            case "annotation-quality" -> "/annotation-quality";
            case "prediction-engine" -> {
                if (action.contains("训练")) {
                    yield "/prediction-engine";
                } else if (action.contains("预测")) {
                    yield "/prediction";
                }
                yield "/prediction-engine";
            }
            case "prediction-evaluation" -> "/model-optimization";
            case "trend-dashboard" -> "/trend-dashboard";
            case "security-audit" -> {
                if (action.contains("日志") || action.contains("审计")) {
                    yield "/audit";
                } else if (action.contains("安全扫描")) {
                    yield "/security";
                } else if (action.contains("权限") || action.contains("备份")) {
                    yield "/security-admin";
                }
                yield "/security-audit";
            }
            default -> "/dashboard";
        };
    }

    private String resolveActionType(String module, String action) {
        return switch (module) {
            case "annotation-platform" -> {
                if (action.contains("导入") || action.contains("上传")) yield "DATA_UPLOAD";
                else if (action.contains("清洗")) yield "DATA_CLEAN";
                else if (action.contains("标签")) yield "LABEL_CONFIG";
                else if (action.contains("标注任务")) yield "ANNOTATION_TASK";
                yield "DATA_COLLECTION";
            }
            case "annotation-quality" -> {
                if (action.contains("抽检")) yield "QUALITY_CHECK";
                else if (action.contains("校验") || action.contains("纠偏")) yield "AUTO_VALIDATE";
                else if (action.contains("一致性")) yield "CONSISTENCY_CHECK";
                yield "QUALITY_MANAGEMENT";
            }
            case "prediction-engine" -> {
                if (action.contains("训练")) yield "TRAIN_MODEL";
                else if (action.contains("预测")) yield "RUN_PREDICTION";
                yield "PREDICTION";
            }
            case "prediction-evaluation" -> {
                if (action.contains("准确率")) yield "ACCURACY_EVAL";
                else if (action.contains("偏差")) yield "BIAS_ANALYSIS";
                else if (action.contains("监控")) yield "MODEL_MONITOR";
                else if (action.contains("调优") || action.contains("重训练")) yield "RETRAINING";
                yield "MODEL_OPTIMIZATION";
            }
            case "trend-dashboard" -> {
                if (action.contains("趋势")) yield "TREND_DIAGNOSIS";
                else if (action.contains("对比")) yield "COMPARISON_ANALYSIS";
                else if (action.contains("异常")) yield "ANOMALY_DETECTION";
                else if (action.contains("根因")) yield "ROOT_CAUSE";
                else if (action.contains("报告")) yield "REPORT_GENERATION";
                yield "TREND_ANALYSIS";
            }
            case "security-audit" -> {
                if (action.contains("日志")) yield "AUDIT_LOG";
                else if (action.contains("扫描")) yield "SECURITY_SCAN";
                else if (action.contains("权限")) yield "PERMISSION_CONFIG";
                else if (action.contains("备份")) yield "DATA_BACKUP";
                yield "SECURITY_AUDIT";
            }
            default -> "DASHBOARD";
        };
    }

    private String resolveActionMessage(String module, String action) {
        return "正在为您跳转到「" + action + "」的真实业务页面，请在对应页面完成操作。";
    }

    private String resolveNextStep(String module, String action) {
        return switch (module) {
            case "annotation-platform" -> "已跳转到数据采集标注页面，请在页面中完成" + action + "操作。";
            case "annotation-quality" -> "已跳转到标注质量管理页面，请在页面中完成质检相关操作。";
            case "prediction-engine" -> "已跳转到预测引擎页面，请在页面中配置并执行预测或训练任务。";
            case "prediction-evaluation" -> "已跳转到模型优化页面，请在页面中查看评估指标并进行优化。";
            case "trend-dashboard" -> "已跳转到趋势分析看板，请在页面中查看趋势、异常检测或生成报告。";
            case "security-audit" -> "已跳转到安全审计相关页面，请在页面中完成安全扫描、日志查询或权限配置。";
            default -> "已跳转到对应业务页面，请继续操作。";
        };
    }

    private String title(String module) {
        return switch (module) {
            case "annotation-platform" -> "数据采集与标注平台";
            case "annotation-quality" -> "标注质量管理模块";
            case "prediction-engine" -> "市场需求预测引擎";
            case "trend-dashboard" -> "趋势分析与可视化";
            case "prediction-evaluation" -> "预测结果评估与优化";
            case "security-audit" -> "数据管理与安全审计";
            default -> "RIver AGI 工作台";
        };
    }

    private String description(String module) {
        return switch (module) {
            case "annotation-platform" -> "用于上传多源数据、配置标签、创建标注任务、分配人员并完成协同标注。";
            case "annotation-quality" -> "用于审核标注结果、检查一致性、评估标注员绩效、处理争议和仲裁。";
            case "prediction-engine" -> "用于创建预测任务、选择算法、训练模型、查看模型版本和 A/B 测试。";
            case "trend-dashboard" -> "用于查看市场趋势、异常波动、根因分析、What-If 推演和可视化报告。";
            case "prediction-evaluation" -> "用于评估预测准确率、监控模型偏差、自动调优并触发 Retraining。";
            case "security-audit" -> "用于查看操作日志、权限分级、安全扫描、脱敏、备份恢复和合规报告。";
            default -> "用于统一查看数据、分析、预测、标注和安全审计任务。";
        };
    }

    private List<String> steps(String module) {
        return switch (module) {
            case "annotation-platform" -> List.of("上传数据或媒体文件", "创建标签体系", "创建并分配标注任务", "提交标注并进入质检");
            case "annotation-quality" -> List.of("选择待审任务", "运行一致性检查", "审核/纠偏异常标注", "提交仲裁或生成质量报告");
            case "prediction-engine" -> List.of("选择数据集和目标字段", "选择预测算法", "训练/运行预测", "管理模型版本与 A/B 测试");
            case "trend-dashboard" -> List.of("运行趋势分析", "检测异常", "查看根因贡献", "生成分析报告");
            case "prediction-evaluation" -> List.of("查看准确率和偏差", "保存监控阈值", "启动自动调优", "应用最优参数或重训练");
            case "security-audit" -> List.of("查询审计日志", "执行安全扫描", "处理风险告警", "配置权限与备份策略");
            default -> List.of("上传数据", "自动分析", "查看结果", "进入对应模块处理");
        };
    }
}
