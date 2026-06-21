package com.arify.httpclientbuilder.entities;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public record HttpResponseEntity(
        int statusCode,
        JsonNode body,
        Map<String, List<String>> headers,
        String url) {
}
