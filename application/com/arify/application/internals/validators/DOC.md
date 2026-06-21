## 1.6. How to use Arify Validator

`ArifyValidator` es una implementación Java pura inspirada en FluentValidation de C# y homologada con `Domain/Commons/Validators` del proyecto Python.

No usa Jakarta, Quarkus ni frameworks externos. Vive en `application/internals/validators` porque valida adapters y reglas de aplicación.

### 1. Modelo sencillo

```java
public record UserRequest(
        String username,
        String email) {
}
```

### 2. Ejemplo de reglas de validación

```java
public class UserRequestValidator extends ArifyValidator {
    public UserRequestValidator(UserRequest obj) {
        super();

        addRules(
            field("username", obj.username())
                .notNull()
                .notEmpty()
                .minLength(5)
                .maxLength(20)
                .withCode("U001")
                .withMessage("El nombre de usuario debe tener entre 5 y 20 caracteres.")
                .validate()
        );

        addRules(
            field("email", obj.email())
                .notNull()
                .notEmpty()
                .minLength(5)
                .maxLength(50)
                .withCode("E001")
                .withMessage("El email debe tener entre 5 y 50 caracteres.")
                .validate()
        );
    }
}
```

### 3. Validador con grupos de validación

```java
public class UserRequestValidator extends ArifyValidator {
    public UserRequestValidator(UserRequest obj) {
        this(obj, "primary");
    }

    public UserRequestValidator(UserRequest obj, String group) {
        super();

        if ("primary".equals(group)) {
            addRules(
                field("email", obj.email())
                    .notNull()
                    .notEmpty()
                    .minLength(5)
                    .maxLength(50)
                    .withCode("E001")
                    .withMessage("El email debe tener entre 5 y 50 caracteres.")
                    .validate()
            );
        } else if ("secondary".equals(group)) {
            addRules(
                field("username", obj.username())
                    .notNull()
                    .minLength(3)
                    .maxLength(30)
                    .withCode("U002")
                    .withMessage("El nombre de usuario debe tener entre 3 y 30 caracteres.")
                    .validate()
            );
        } else {
            throw new IllegalArgumentException("Grupo de validación desconocido: " + group);
        }
    }

    public static UserRequestValidator fromSecondary(UserRequest obj) {
        return new UserRequestValidator(obj, "secondary");
    }
}
```

### 4. Ejecutar validación

```java
UserRequest request = new UserRequest("   ", "Msg1@email.com");

List<ValidationResultAdapter> errors = FluentValidationExecutor.execute(
        request,
        UserRequestValidator::new);

if (!errors.isEmpty()) {
    for (ValidationResultAdapter error : errors) {
        System.out.printf("Error en '%s': [%s] %s%n", error.field(), error.code(), error.message());
    }
}
```

### 5. Reglas disponibles

| Método | Descripción |
|--------|-------------|
| `notNull()` | Falla si el valor es `null` |
| `notEmpty()` | Falla si el string está vacío o la colección/map está vacía |
| `minLength(int)` | Falla si un string tiene menos caracteres que el mínimo |
| `maxLength(int)` | Falla si un string excede el máximo |
| `isNumeric()` | Falla si el valor no es un string numérico |
| `withCode(String)` | Reemplaza el código de la última regla rota |
| `withMessage(String)` | Reemplaza el mensaje de la última regla rota |
| `when(boolean)` | Descarta la última regla rota si la condición es falsa |
| `validate()` | Retorna las reglas rotas acumuladas |

### 6. Diferencia con Python

Python usa `field(obj, obj.username)` y obtiene el nombre del campo con `__dict__`.

Java records no tienen `__dict__`, por eso el nombre se pasa explícitamente:

```java
field("username", obj.username())
```

El comportamiento de validación y el estilo fluent se mantienen homologados.
