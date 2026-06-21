package com.arify.application.internals.validators;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;

/**
 * Utilidad para extraer metadatos de lambdas serializables.
 * Permite obtener el nombre de la propiedad desde una referencia a método.
 * 
 * Equivalente a la inspección de Expression Trees en C# FluentValidation.
 * Usa el mecanismo interno de Java SerializedLambda para evitar reflexión pesada.
 */
public final class LambdaUtils {
    
    private LambdaUtils() {
        // Utility class, no se permite instanciación
    }

    /**
     * Extrae el nombre del campo desde una referencia a método serializable.
     * 
     * Soporta:
     * - Records de Java: MyRecord::fieldName
     * - POJOs con getters: MyPojo::getFieldName -> fieldName
     * 
     * @param propertyFunction Referencia al método (ej. TraceIdentifierAdapter::deviceIdentifier)
     * @return Nombre del campo en camelCase
     * @throws IllegalArgumentException Si no se puede extraer el nombre
     */
    public static <T, P> String getPropertyName(PropertyFunction<T, P> propertyFunction) {
        try {
            Method writeReplaceMethod = propertyFunction.getClass().getDeclaredMethod("writeReplace");
            writeReplaceMethod.setAccessible(true);
            SerializedLambda serializedLambda = (SerializedLambda) writeReplaceMethod.invoke(propertyFunction);
            
            String methodName = serializedLambda.getImplMethodName();
            
            // Convertir el nombre del método al nombre del campo
            return methodNameToFieldName(methodName);
            
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(
                    "No se pudo extraer el nombre de la propiedad. "
                    + "Asegúrate de usar una referencia a método (method reference) serializable.", 
                    e);
        }
    }

    /**
     * Convierte el nombre de un método a nombre de campo.
     * 
     * Ejemplos:
     * - getDeviceIdentifier -> deviceIdentifier (getter estándar)
     * - deviceIdentifier -> deviceIdentifier (accessor de Record)
     * - isActive -> active (getter booleano)
     * 
     * @param methodName Nombre del método extraído del SerializedLambda
     * @return Nombre del campo en camelCase
     */
    private static String methodNameToFieldName(String methodName) {
        // Caso 1: Método de Record (sin prefijo get/is)
        if (!methodName.startsWith("get") && !methodName.startsWith("is")) {
            return methodName;
        }
        
        // Caso 2: Getter estándar (getFieldName)
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return decapitalize(methodName.substring(3));
        }
        
        // Caso 3: Getter booleano (isActive)
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return decapitalize(methodName.substring(2));
        }
        
        // Fallback: retornar tal cual
        return methodName;
    }

    /**
     * Convierte la primera letra de un string a minúscula.
     * 
     * Ejemplos:
     * - DeviceIdentifier -> deviceIdentifier
     * - Active -> active
     * 
     * @param str String a convertir
     * @return String con primera letra en minúscula
     */
    private static String decapitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        
        if (str.length() == 1) {
            return str.toLowerCase();
        }
        
        // Casos especiales: si las primeras dos letras son mayúsculas, no convertir
        // Ejemplo: "URL" -> "URL" (no "uRL")
        if (Character.isUpperCase(str.charAt(0)) && Character.isUpperCase(str.charAt(1))) {
            return str;
        }
        
        return Character.toLowerCase(str.charAt(0)) + str.substring(1);
    }
}
