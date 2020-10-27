package com.google.inject.internal;

import com.google.common.collect.Lists;
import com.google.inject.spi.ErrorDetail;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

/** Error reported by Guice when a scope annotation is not bound to any scope implementation. */
final class ScopeNotFoundError extends InternalErrorDetail<ScopeNotFoundError> {

  private final Class<? extends Annotation> scopeAnnotation;

  ScopeNotFoundError(Class<? extends Annotation> scopeAnnotation, List<Object> sources) {
    super(
        ErrorId.SCOPE_NOT_FOUND,
        String.format("No scope is bound to %s.", Messages.convert(scopeAnnotation)),
        sources,
        null);
    this.scopeAnnotation = scopeAnnotation;
  }

  @Override
  public boolean isMergeable(ErrorDetail<?> other) {
    return other instanceof ScopeNotFoundError
        && ((ScopeNotFoundError) other).scopeAnnotation.equals(scopeAnnotation);
  }

  @Override
  protected void formatDetail(List<ErrorDetail<?>> mergeableErrors, Formatter formatter) {
    List<List<Object>> sourcesSet = new ArrayList<>();
    sourcesSet.add(getSources());
    mergeableErrors.stream().map(ErrorDetail::getSources).forEach(sourcesSet::add);

    formatter.format("%n%s%n", "Used at:");
    int sourceListIndex = 1;
    for (List<Object> sources : sourcesSet) {
      ErrorFormatter.formatSources(sourceListIndex++, Lists.reverse(sources), formatter);
    }
  }

  @Override
  public ScopeNotFoundError withSources(List<Object> newSources) {
    return new ScopeNotFoundError(scopeAnnotation, newSources);
  }
}
