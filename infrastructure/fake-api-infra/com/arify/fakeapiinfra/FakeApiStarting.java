package com.arify.fakeapiinfra;

import java.time.Duration;

/* ********************************************************************************************************
 * Copyright © 2026 Arify Labs - All rights reserved.
 *
 * Info                  : Fake API configuration holder (hosts, timeouts) read from environment variables.
 *
 * By                    : Victor Jhampier Caxi Maquera
 * Email/Mobile/Phone    : victorjhampier@gmail.com | 968991*14
 *
 * Creation date         : 22/06/2026 3:05h
 **********************************************************************************************************/


public final class FakeApiStarting {
    public static final String EXAMPLE_HOST_BASE = envOrDefault("EXAMPLE_HOST_BASE", "https://jsonplaceholder.typicode.com");
    public static final String EXAMPLE_TITLE_BASE = envOrDefault("EXAMPLE_TITLE_BASE", "https://fakerapi.it");
    public static final Duration TIMEOUT = Duration.ofSeconds(9);

    public static final String GET_USER_OPERATION = "FakeApiCommand.get_user_async";
    public static final String GET_TITLE_OPERATION = "FakeApiCommand.get_title_async";
    public static final String USER_KEYWORD = "my_user_id";
    public static final String TITLE_KEYWORD = "my_title_id";

    private FakeApiStarting() {
    }

    public static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
