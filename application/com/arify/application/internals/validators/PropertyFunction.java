package com.arify.application.internals.validators;

import java.io.Serializable;
import java.util.function.Function;

/**
 * Interfaz funcional que extiende Function y Serializable.
 * Permite capturar metadatos de referencias a métodos (method references)
 * para extraer automáticamente el nombre de la propiedad.
 * 
 * Equivalente al uso de Expression<Func<T, P>> en C# FluentValidation,
 * pero usando la reflexión de SerializedLambda en Java.
 * 
 * @param <T> Tipo del objeto que contiene la propiedad
 * @param <P> Tipo de la propiedad
 */
@FunctionalInterface
public interface PropertyFunction<T, P> extends Function<T, P>, Serializable {
}
