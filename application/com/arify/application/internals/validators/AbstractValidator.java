package com.arify.application.internals.validators;

import java.util.ArrayList;
import java.util.List;

/**
 * Clase base abstracta para validadores de entidades.
 * Implementa el patrón Fluent Validation de C# .NET para Java 21.
 * 
 * Características:
 * - Evaluación perezosa (lazy): las reglas se definen en el constructor pero solo se ejecutan al llamar validate()
 * - Type-safe: usa genéricos para evitar errores en tiempo de compilación
 * - Extracción automática de nombres de campos desde method references
 * 
 * Ejemplo de uso:
 * <pre>
 * public class UserValidator extends AbstractValidator<User> {
 *     public UserValidator() {
 *         ruleFor(User::email)
 *             .notNull().withErrorCode("E001")
 *             .notEmpty().withMessage("Email is required");
 *     }
 * }
 * </pre>
 * 
 * Equivalente a AbstractValidator<T> de FluentValidation en C# .NET.
 * 
 * @param <T> Tipo del objeto a validar
 */
public abstract class AbstractValidator<T> {
    
    private final List<RuleBuilder<T, ?>> ruleBuilders = new ArrayList<>();

    /**
     * Define una regla de validación para una propiedad del objeto.
     * El nombre del campo se extrae automáticamente desde la referencia al método.
     * 
     * Equivalente a RuleFor(x => x.Property) en C# FluentValidation.
     * 
     * @param <P> Tipo de la propiedad
     * @param propertyExtractor Referencia al método getter (ej. User::getEmail o User::email)
     * @return Builder fluido para encadenar validaciones
     */
    protected <P> RuleBuilder<T, P> ruleFor(PropertyFunction<T, P> propertyExtractor) {
        String fieldName = LambdaUtils.getPropertyName(propertyExtractor);
        RuleBuilder<T, P> builder = new RuleBuilder<>(fieldName, propertyExtractor);
        ruleBuilders.add(builder);
        return builder;
    }

    /**
     * Ejecuta todas las reglas de validación definidas contra una instancia específica.
     * 
     * Este método es llamado por FluentValidationExecutor después de instanciar el validador.
     * Las reglas se evalúan de forma secuencial y se coleccionan todos los errores.
     * 
     * @param instance Instancia del objeto a validar
     * @return Lista inmutable de errores de validación (vacía si no hay errores)
     */
    public List<ArifyValidationRuleResponse> validate(T instance) {
        List<ArifyValidationRuleResponse> brokenRules = new ArrayList<>();
        
        for (RuleBuilder<T, ?> builder : ruleBuilders) {
            builder.execute(instance, brokenRules);
        }
        
        return List.copyOf(brokenRules);
    }

    /**
     * Retorna el número de reglas registradas en este validador.
     * Útil para testing y debugging.
     * 
     * @return Cantidad de builders de reglas registrados
     */
    protected int getRuleCount() {
        return ruleBuilders.size();
    }
}
