package com.arify.domain.entities;

import java.util.List;
import java.util.Map;

public record HttpResponseEntity(
        int statusCode,
        String body,
        Map<String, List<String>> headers,
        String url) {
}
