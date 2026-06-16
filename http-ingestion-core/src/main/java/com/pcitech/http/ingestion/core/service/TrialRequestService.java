package com.pcitech.http.ingestion.core.service;

import com.pcitech.http.ingestion.core.dto.TrialRequestDto;
import com.pcitech.http.ingestion.core.dto.TrialResponseDto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class TrialRequestService {

    private static final int MAX_BODY_BYTES = 1_048_576;

    private final WebClient.Builder webClientBuilder;

    public TrialRequestService(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public TrialResponseDto execute(TrialRequestDto request) {
        long start = System.currentTimeMillis();
        HttpMethod method;
        try {
            method = HttpMethod.valueOf(request.method().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return TrialResponseDto.failure("Invalid HTTP method: " + request.method(), System.currentTimeMillis() - start);
        }

        WebClient client = webClientBuilder.build();

        try {
            ResponseHolder holder = client.method(method)
                    .uri(buildUri(request.url(), request.query()))
                    .headers(headers -> applyHeaders(headers, request))
                    .body(resolveBodyInserter(method, request))
                    .exchangeToMono(response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> {
                                Map<String, String> headerMap = new HashMap<>();
                                response.headers().asHttpHeaders().forEach((name, values) -> {
                                    if (!values.isEmpty()) {
                                        headerMap.put(name, String.join(", ", values));
                                    }
                                });
                                return new ResponseHolder(response.statusCode().value(), body, headerMap);
                            }))
                    .timeout(Duration.ofMillis(request.timeoutMs()))
                    .block();

            long duration = System.currentTimeMillis() - start;
            if (holder == null) {
                return TrialResponseDto.failure("Empty response", duration);
            }
            return toTrialResponse(holder.statusCode(), holder.body(), duration, holder.responseHeaders());
        } catch (WebClientResponseException ex) {
            long duration = System.currentTimeMillis() - start;
            return toTrialResponse(
                    ex.getStatusCode().value(),
                    ex.getResponseBodyAsString(),
                    duration,
                    toHeaderMap(ex.getHeaders()));
        } catch (Exception ex) {
            return TrialResponseDto.failure(ex.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private BodyInserter<?, ? super org.springframework.http.client.reactive.ClientHttpRequest> resolveBodyInserter(
            HttpMethod method,
            TrialRequestDto request
    ) {
        if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
            return BodyInserters.empty();
        }
        return switch (request.bodyType()) {
            case "form-urlencoded" -> BodyInserters.fromFormData(toFormData(request.form()));
            case "json" -> BodyInserters.fromValue(request.body() == null ? "" : request.body());
            default -> BodyInserters.empty();
        };
    }

    private MultiValueMap<String, String> toFormData(Map<String, String> form) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        if (form != null) {
            form.forEach(formData::add);
        }
        return formData;
    }

    private TrialResponseDto toTrialResponse(
            int statusCode,
            String body,
            long duration,
            Map<String, String> responseHeaders
    ) {
        String safeBody = body == null ? "" : body;
        long length = safeBody.getBytes(StandardCharsets.UTF_8).length;
        boolean truncated = length > MAX_BODY_BYTES;
        String output = truncated ? truncateUtf8(safeBody, MAX_BODY_BYTES) : safeBody;
        return TrialResponseDto.success(statusCode, duration, output, truncated, length, responseHeaders);
    }

    private Map<String, String> toHeaderMap(HttpHeaders headers) {
        Map<String, String> result = new HashMap<>();
        if (headers == null) {
            return result;
        }
        headers.forEach((name, values) -> {
            if (values != null && !values.isEmpty()) {
                result.put(name, String.join(", ", values));
            }
        });
        return result;
    }

    private String truncateUtf8(String value, int maxBytes) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return value;
        }
        return new String(bytes, 0, maxBytes, StandardCharsets.UTF_8);
    }

    private String buildUri(String url, Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return url;
        }
        StringBuilder sb = new StringBuilder(url);
        sb.append(url.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, String> entry : query.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }

    private void applyHeaders(HttpHeaders headers, TrialRequestDto request) {
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (request.headers() != null) {
            request.headers().forEach(headers::set);
        }
        if (headers.getContentType() == null) {
            if ("json".equals(request.bodyType())) {
                headers.setContentType(MediaType.APPLICATION_JSON);
            } else if ("form-urlencoded".equals(request.bodyType())) {
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            }
        }
    }

    private record ResponseHolder(int statusCode, String body, Map<String, String> responseHeaders) {
    }
}
