package com.river.agi.common;

public final class ErrorCode {
    
    public static final int SUCCESS = 200;
    
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int METHOD_NOT_ALLOWED = 405;
    public static final int CONFLICT = 409;
    public static final int TOO_MANY_REQUESTS = 429;
    
    public static final int INTERNAL_SERVER_ERROR = 500;
    public static final int SERVICE_UNAVAILABLE = 503;
    
    public static final int DATASET_NOT_FOUND = 1001;
    public static final int DATASET_UPLOAD_FAILED = 1002;
    public static final int DATASET_PARSE_FAILED = 1003;
    public static final int DATASET_ALREADY_EXISTS = 1004;
    
    public static final int SECURITY_SCAN_FAILED = 2001;
    public static final int SECURITY_SCAN_NOT_FOUND = 2002;
    public static final int MASK_OPERATION_FAILED = 2003;
    
    public static final int ANALYSIS_TASK_NOT_FOUND = 3001;
    public static final int ANALYSIS_TASK_FAILED = 3002;
    
    public static final int PREDICTION_TASK_NOT_FOUND = 4001;
    public static final int PREDICTION_TASK_FAILED = 4002;
    public static final int MODEL_TRAINING_FAILED = 4003;
    
    public static final int ANNOTATION_TASK_NOT_FOUND = 5001;
    public static final int ANNOTATION_SUBMISSION_FAILED = 5002;
    
    public static final int CHAT_SESSION_NOT_FOUND = 6001;
    public static final int CHAT_MESSAGE_FAILED = 6002;
    
    public static final int RESOURCE_ACCESS_DENIED = 7001;
    public static final int RESOURCE_NOT_FOUND = 7002;
    
    public static final int TASK_NOT_FOUND = 8001;
    public static final int TASK_CANCELLED = 8002;
    public static final int TASK_ALREADY_COMPLETED = 8003;
    
    private ErrorCode() {
    }
    
    public static String getMessage(int code) {
        return switch (code) {
            case SUCCESS -> "success";
            case BAD_REQUEST -> "Bad request";
            case UNAUTHORIZED -> "Unauthorized";
            case FORBIDDEN -> "Forbidden";
            case NOT_FOUND -> "Not found";
            case INTERNAL_SERVER_ERROR -> "Internal server error";
            case DATASET_NOT_FOUND -> "Dataset not found";
            case DATASET_UPLOAD_FAILED -> "Dataset upload failed";
            case DATASET_PARSE_FAILED -> "Dataset parse failed";
            case SECURITY_SCAN_FAILED -> "Security scan failed";
            case RESOURCE_ACCESS_DENIED -> "Access denied";
            case RESOURCE_NOT_FOUND -> "Resource not found";
            default -> "Unknown error";
        };
    }
}
