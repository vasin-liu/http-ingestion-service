package com.pcitech.http.ingestion.core.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TransformPipeline {

    private static final DateTimeFormatter DAHUA_UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter MEIYA_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);

    private final JsonPathSupport jsonPathSupport;
    private final ObjectMapper objectMapper;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    public TransformPipeline(JsonPathSupport jsonPathSupport, ObjectMapper objectMapper) {
        this.jsonPathSupport = jsonPathSupport;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> transform(List<Object> sourceRecords, RuntimeConnectorConfig.TransformSettings transform) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (transform == null) {
            for (Object record : sourceRecords) {
                results.add(asMap(record));
            }
            return results;
        }
        int index = 0;
        for (Object record : sourceRecords) {
            try {
                Map<String, Object> current = asMap(record);
                for (RuntimeConnectorConfig.TransformStep step : transform.steps()) {
                    current = applyStep(current, step);
                    if (current == null) {
                        break;
                    }
                }
                if (current != null) {
                    results.add(current);
                }
            } catch (Exception ex) {
                throw new TransformException(index, ex.getMessage(), ex);
            }
            index++;
        }
        return results;
    }

    public TransformResult transformTolerant(
            List<Object> sourceRecords,
            RuntimeConnectorConfig.TransformSettings transform,
            int maxErrors
    ) {
        List<Map<String, Object>> results = new ArrayList<>();
        List<TransformError> errors = new ArrayList<>();
        if (transform == null) {
            for (Object record : sourceRecords) {
                results.add(asMap(record));
            }
            return new TransformResult(results, errors);
        }
        int index = 0;
        for (Object record : sourceRecords) {
            try {
                Map<String, Object> current = asMap(record);
                for (RuntimeConnectorConfig.TransformStep step : transform.steps()) {
                    current = applyStep(current, step);
                    if (current == null) {
                        break;
                    }
                }
                if (current != null) {
                    results.add(current);
                }
            } catch (Exception ex) {
                if (errors.size() < maxErrors) {
                    errors.add(new TransformError(index, ex.getMessage(), toSampleJson(record)));
                }
            }
            index++;
        }
        return new TransformResult(results, errors);
    }

    private String toSampleJson(Object record) {
        try {
            String json = objectMapper.writeValueAsString(record);
            return json.length() <= 2000 ? json : json.substring(0, 2000);
        } catch (Exception ex) {
            return String.valueOf(record);
        }
    }

    private Map<String, Object> applyStep(Map<String, Object> record, RuntimeConnectorConfig.TransformStep step) {
        return switch (step.type()) {
            case "filter" -> matches(record, step.condition()) ? record : null;
            case "map_fields" -> mapFields(record, step.mappings());
            case "expression" -> applyExpressions(record, step.expressions());
            default -> record;
        };
    }

    private Map<String, Object> mapFields(Map<String, Object> record, List<RuntimeConnectorConfig.FieldMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return identityMap(record);
        }
        Map<String, Object> mapped = new HashMap<>();
        for (RuntimeConnectorConfig.FieldMapping mapping : mappings) {
            Object value = jsonPathSupport.readValue(record, mapping.source());
            mapped.put(mapping.target(), convertType(value, mapping.type(), mapping.sourceFormat(), mapping.targetFormat()));
        }
        return mapped;
    }

    private Map<String, Object> identityMap(Map<String, Object> record) {
        Map<String, Object> mapped = new HashMap<>();
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            mapped.put(entry.getKey(), entry.getValue());
        }
        return mapped;
    }

    private Map<String, Object> applyExpressions(Map<String, Object> record, Map<String, String> expressions) {
        if (expressions == null || expressions.isEmpty()) {
            return record;
        }
        Map<String, Object> copy = new HashMap<>(record);
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("record", copy);
        for (Map.Entry<String, String> entry : expressions.entrySet()) {
            String spel = toSpel(entry.getValue());
            Object value = expressionParser.parseExpression(spel).getValue(context);
            copy.put(entry.getKey(), value);
        }
        return copy;
    }

    private boolean matches(Map<String, Object> record, String condition) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("record", record);
        Boolean result = expressionParser.parseExpression(toSpel(condition)).getValue(context, Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    private Object convertType(Object value, String type, String sourceFormat, String targetFormat) {
        if (value == null || type == null) {
            return value;
        }
        return switch (type) {
            case "long" -> value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
            case "double" -> value instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(value));
            case "boolean" -> value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
            case "datetime" -> convertDateTime(value, sourceFormat, targetFormat);
            case "decimal" -> value instanceof BigDecimal bd ? bd : new BigDecimal(String.valueOf(value));
            default -> String.valueOf(value);
        };
    }

    private Object convertDateTime(Object value, String sourceFormat, String targetFormat) {
        Instant instant = parseInstant(value, sourceFormat);
        if (instant == null) {
            return String.valueOf(value);
        }
        String outFormat = targetFormat == null || targetFormat.isBlank() ? "iso_instant" : targetFormat;
        return formatInstant(instant, outFormat);
    }

    private Instant parseInstant(Object value, String sourceFormat) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Number number) {
            return Instant.ofEpochMilli(number.longValue());
        }
        String text = String.valueOf(value);
        String format = sourceFormat == null || sourceFormat.isBlank() ? "iso_instant" : sourceFormat;
        return switch (format) {
            case "epoch_ms" -> {
                try {
                    yield Instant.ofEpochMilli(Long.parseLong(text));
                } catch (NumberFormatException ex) {
                    yield null;
                }
            }
            case "dahua_utc" -> {
                try {
                    yield DAHUA_UTC.parse(text, Instant::from);
                } catch (DateTimeParseException ex) {
                    yield null;
                }
            }
            case "meiya_datetime" -> {
                try {
                    yield MEIYA_DATETIME.parse(text, Instant::from);
                } catch (DateTimeParseException ex) {
                    yield null;
                }
            }
            default -> {
                try {
                    yield Instant.parse(text);
                } catch (DateTimeParseException ex) {
                    yield null;
                }
            }
        };
    }

    private String formatInstant(Instant instant, String format) {
        return switch (format) {
            case "epoch_ms" -> String.valueOf(instant.toEpochMilli());
            case "dahua_utc" -> DAHUA_UTC.format(instant);
            case "meiya_datetime" -> MEIYA_DATETIME.format(instant);
            default -> instant.toString();
        };
    }

    private Map<String, Object> asMap(Object record) {
        if (record instanceof Map<?, ?> map) {
            Map<String, Object> copy = new HashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), v));
            return copy;
        }
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("value", record);
        return wrapper;
    }

    private String toSpel(String expression) {
        String spel = expression;
        spel = spel.replace("${record.", "#record['");
        spel = spel.replace("}", "']");
        return spel;
    }

    public record TransformResult(List<Map<String, Object>> records, List<TransformError> errors) {
    }

    public record TransformError(int recordIndex, String message, String sampleJson) {
    }

    public static class TransformException extends RuntimeException {
        private final int recordIndex;

        public TransformException(int recordIndex, String message, Throwable cause) {
            super(message, cause);
            this.recordIndex = recordIndex;
        }

        public int recordIndex() {
            return recordIndex;
        }
    }
}
