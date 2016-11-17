package com.google.inject.examples.memory;

import static java.lang.annotation.RetentionPolicy.*;

import java.lang.annotation.Retention;

@Retention(RUNTIME)
public @interface FromFlash {}
