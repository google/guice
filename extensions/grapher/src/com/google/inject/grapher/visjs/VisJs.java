package com.google.inject.grapher.visjs;

import com.google.inject.BindingAnnotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author ksaric
 */
@BindingAnnotation
@Retention(RetentionPolicy.RUNTIME)
@interface VisJs {}
