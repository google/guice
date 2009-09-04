package com.google.inject.lifecycle;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** @author dhanji@google.com (Dhanji R. Prasanna) */
@Retention(RetentionPolicy.RUNTIME)
@BindingAnnotation
@interface ListOfMatchers {
}
