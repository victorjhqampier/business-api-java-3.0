package com.arify.helpers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper para generar nombres de campo BIAN dinámicos basados en el tipo de dato.
 * Equivalente a SystemAPI/Handlers/OpenApi/BianResponseNameHelper.cs
 * 
 * Ejemplo: CreateExampleAdapter → CreateExample → CreateExampleResponse
 */
public final class BianResponseNameHelper {
    private static final String ADAPTER_SUFFIX = "Adapter";
    private static final String HELPER_SUFFIX = "Helper";
    private static final String RESPONSE_SUFFIX = "Response";
    private static final String DEFAULT_FIELD_NAME = "data";

    private static final Map<Class<?>, String> FIELD_NAME_CACHE = new ConcurrentHashMap<>();

    private BianResponseNameHelper() {
    }

    /**
     * Obtiene el nombre de campo BIAN para un tipo específico.
     * Usa cache para evitar recalcular en hot path.
     */
    public static String getResponseFieldName(Class<?> type) {
        return FIELD_NAME_CACHE.computeIfAbsent(type, BianResponseNameHelper::generateResponseFieldName);
    }

    /**
     * Obtiene el nombre de campo BIAN desde una instancia (usa runtime type).
     * Si data es null, retorna "data" como fallback.
     */
    public static String getResponseFieldNameFromObject(Object data) {
        if (data == null) {
            return DEFAULT_FIELD_NAME;
        }
        return FIELD_NAME_CACHE.computeIfAbsent(data.getClass(), BianResponseNameHelper::generateResponseFieldName);
    }

    /**
     * Genera el nombre de campo BIAN:
     * 1. Toma el simple name del tipo
     * 2. Remueve sufijos conocidos (Response, Adapter, Helper)
     * 3. Agrega "Response" al final
     */
    private static String generateResponseFieldName(Class<?> type) {
        String typeName = type.getSimpleName();
        String baseName = getBaseName(typeName);
        return baseName + RESPONSE_SUFFIX;
    }

    /**
     * Extrae el nombre base removiendo sufijos conocidos en orden de prioridad.
     */
    private static String getBaseName(String typeName) {
        // Si termina con "Response", removerlo y chequear otros sufijos
        if (typeName.endsWith(RESPONSE_SUFFIX)) {
            String withoutResponse = typeName.substring(0, typeName.length() - RESPONSE_SUFFIX.length());
            
            if (withoutResponse.endsWith(ADAPTER_SUFFIX)) {
                return withoutResponse.substring(0, withoutResponse.length() - ADAPTER_SUFFIX.length());
            }
            
            return withoutResponse;
        }
        
        // Si termina con "Adapter", removerlo
        if (typeName.endsWith(ADAPTER_SUFFIX)) {
            return typeName.substring(0, typeName.length() - ADAPTER_SUFFIX.length());
        }
        
        // Si termina con "Helper", removerlo
        if (typeName.endsWith(HELPER_SUFFIX)) {
            return typeName.substring(0, typeName.length() - HELPER_SUFFIX.length());
        }
        
        // Si no tiene sufijos conocidos, retornar el nombre completo
        return typeName;
    }
}
