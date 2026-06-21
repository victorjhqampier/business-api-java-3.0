package com.arify.domain.entities;

public enum InternalApiMessage {
    _15083("No puede ser vacio"),
    _15084("No puede ser nulo"),
    _15085("Nombre no es valido"),
    _15086("Tipo de campo no valido"),
    _15087("Valor de campo esta fuera de los limites permitidos"),
    _15088("Valor de campo no es valido"),
    _15089("Ingrese caracteres alfabeticos (a-z)"),
    _15090("Ingrese caracteres alfanumericos (a-z y 0-9)"),
    _15091("Ingrese caracteres numericos (0-9)"),
    _15092("Ingrese caracteres permitidos"),
    _15093("Ingrese un nombre valido"),
    _15094("Ingrese un tipo valido"),
    _15095("Ingrese un valor valido"),
    _15096("Longitud del valor esta fuera de los limites permitidos"),
    _15097("No se debe ingresar un valor"),
    _15098("Parametros incorrectos"),
    _15099("Error interno del servidor");

    public final String value;

    InternalApiMessage(String value) {
        this.value = value;
    }
}
