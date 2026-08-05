package com.river.agi.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/schema")
@Profile("mysql")
@RequiredArgsConstructor
public class SchemaFixController {

    private final JdbcTemplate jdbcTemplate;

    private static final List<String> FIX_COLUMNS = Arrays.asList(
            "ALTER TABLE collection_task ADD COLUMN deleted INT DEFAULT 0",
            "ALTER TABLE collection_task ADD COLUMN media_type VARCHAR(50)",
            "ALTER TABLE collection_task ADD COLUMN source_uri VARCHAR(500)",
            "ALTER TABLE collection_task ADD COLUMN dataset_id BIGINT",
            "ALTER TABLE collection_task ADD COLUMN label_schema_id BIGINT",
            "ALTER TABLE collection_task ADD COLUMN cleaning_config_json TEXT",
            "ALTER TABLE collection_task ADD COLUMN cleaning_summary_json TEXT",
            "ALTER TABLE collection_task ADD COLUMN annotation_rule_json TEXT",
            "ALTER TABLE collection_task ADD COLUMN collaboration_mode VARCHAR(20) DEFAULT 'SINGLE'",
            "ALTER TABLE collection_task ADD COLUMN assigned_annotators VARCHAR(500)",
            "ALTER TABLE collection_task ADD COLUMN total_items INT DEFAULT 0",
            "ALTER TABLE collection_task ADD COLUMN completed_items INT DEFAULT 0",
            "ALTER TABLE annotation_task ADD COLUMN description VARCHAR(1000)",
            "ALTER TABLE annotation_task ADD COLUMN assigned_annotators INT",
            "ALTER TABLE annotation_task ADD COLUMN quality_report_json TEXT",
            "ALTER TABLE annotation_task ADD COLUMN review_count INT DEFAULT 0",
            "ALTER TABLE annotation_task ADD COLUMN arbitration_count INT DEFAULT 0",
            "ALTER TABLE annotation_task ADD COLUMN pass_rate DOUBLE",
            "ALTER TABLE annotation_task ADD COLUMN consistency_rate DOUBLE",
            "ALTER TABLE annotation_task ADD COLUMN publish_version VARCHAR(50)",
            "ALTER TABLE annotation_task ADD COLUMN published_at TIMESTAMP NULL",
            "ALTER TABLE annotation_item ADD COLUMN label_name VARCHAR(200)",
            "ALTER TABLE annotation_item ADD COLUMN comment VARCHAR(1000)",
            "ALTER TABLE annotation_item ADD COLUMN reviewed_by BIGINT",
            "ALTER TABLE annotation_item ADD COLUMN review_comment VARCHAR(1000)",
            "ALTER TABLE annotation_item ADD COLUMN annotated_at TIMESTAMP NULL",
            "ALTER TABLE annotation_item ADD COLUMN reviewed_at TIMESTAMP NULL",
            "ALTER TABLE annotation_item ADD COLUMN is_corrected BOOLEAN DEFAULT FALSE",
            "ALTER TABLE annotation_item ADD COLUMN original_confidence DECIMAL(5,4)",
            "ALTER TABLE annotation_item ADD COLUMN original_label_code VARCHAR(100)",
            "ALTER TABLE annotation_item ADD COLUMN corrected_at TIMESTAMP NULL",
            "ALTER TABLE prediction_task ADD COLUMN task_type VARCHAR(20) DEFAULT 'REGRESSION'",
            "ALTER TABLE prediction_task ADD COLUMN dl_model_id VARCHAR(100)",
            "ALTER TABLE security_policy ADD COLUMN rules TEXT",
            "ALTER TABLE security_policy ADD COLUMN description VARCHAR(500)",
            "ALTER TABLE security_policy ADD COLUMN priority INT DEFAULT 0",
            "ALTER TABLE security_policy ADD COLUMN is_enabled BOOLEAN DEFAULT TRUE",
            "ALTER TABLE prediction_algorithm_config ADD COLUMN algorithm_family VARCHAR(50)",
            "ALTER TABLE prediction_algorithm_config ADD COLUMN task_type VARCHAR(20)",
            "ALTER TABLE prediction_algorithm_config ADD COLUMN default_params TEXT",
            "ALTER TABLE prediction_algorithm_config ADD COLUMN is_default BOOLEAN DEFAULT FALSE",
            "ALTER TABLE prediction_algorithm_config ADD COLUMN priority INT DEFAULT 0"
    );

    @GetMapping("/fix")
    public Map<String, Object> fixSchema() {
        log.info("=== Manual schema fix triggered ===");
        int fixed = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (String sql : FIX_COLUMNS) {
            String columnName = extractColumnName(sql);
            String tableName = extractTableName(sql);
            try {
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                        Integer.class, tableName, columnName);
                if (count != null && count > 0) {
                    skipped++;
                } else {
                    jdbcTemplate.execute(sql);
                    fixed++;
                    log.info("  Added {}.{}", tableName, columnName);
                }
            } catch (Exception e) {
                errors.add(tableName + "." + columnName + ": " + e.getMessage());
                log.warn("  Error fixing {}.{}: {}", tableName, columnName, e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("fixed", fixed);
        result.put("skipped", skipped);
        result.put("errors", errors);
        result.put("message", "Schema fix complete");

        log.info("=== Schema fix complete: fixed={}, skipped={}, errors={} ===", fixed, skipped, errors.size());
        return result;
    }

    private String extractColumnName(String sql) {
        int colIdx = sql.indexOf("ADD COLUMN ");
        if (colIdx >= 0) {
            String rest = sql.substring(colIdx + 12);
            int spaceIdx = rest.indexOf(' ');
            return spaceIdx > 0 ? rest.substring(0, spaceIdx) : rest;
        }
        return "unknown";
    }

    private String extractTableName(String sql) {
        int tableIdx = sql.indexOf("ALTER TABLE ");
        if (tableIdx >= 0) {
            String rest = sql.substring(tableIdx + 12);
            int spaceIdx = rest.indexOf(' ');
            return spaceIdx > 0 ? rest.substring(0, spaceIdx) : rest;
        }
        return "unknown";
    }
}
