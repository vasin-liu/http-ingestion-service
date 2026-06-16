package com.pcitech.http.ingestion.core.engine;

import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LinkHeaderSupport {

    private static final Pattern LINK_PART = Pattern.compile("<([^>]+)>\\s*;\\s*rel=\"?([^\",]+)\"?");

    private LinkHeaderSupport() {
    }

    static Optional<String> parseRelLink(String headerValue, String rel) {
        if (headerValue == null || headerValue.isBlank() || rel == null || rel.isBlank()) {
            return Optional.empty();
        }
        String targetRel = rel.trim();
        for (String part : splitLinkHeader(headerValue)) {
            Matcher matcher = LINK_PART.matcher(part.trim());
            if (matcher.find() && targetRel.equalsIgnoreCase(matcher.group(2).trim())) {
                String url = matcher.group(1).trim();
                if (!url.isBlank()) {
                    return Optional.of(url);
                }
            }
        }
        return Optional.empty();
    }

    static String readHeader(Map<String, String> responseHeaders, String headerName) {
        if (responseHeaders == null || responseHeaders.isEmpty() || headerName == null || headerName.isBlank()) {
            return null;
        }
        for (Map.Entry<String, String> entry : responseHeaders.entrySet()) {
            if (headerName.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    static boolean shouldStop(
            RuntimeConnectorConfig.PaginationSettings pagination,
            List<Object> pageRecords,
            Optional<String> nextLink
    ) {
        List<String> rules = pagination.stopWhen() == null || pagination.stopWhen().isEmpty()
                ? RuntimeConnectorConfig.PaginationSettings.defaultLinkHeaderStopWhen()
                : pagination.stopWhen();
        for (String rule : rules) {
            if ("empty_page".equalsIgnoreCase(rule) && pageRecords.isEmpty()) {
                return true;
            }
            if ("no_next_link".equalsIgnoreCase(rule) && nextLink.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static List<String> splitLinkHeader(String headerValue) {
        return List.of(headerValue.split(","));
    }
}
