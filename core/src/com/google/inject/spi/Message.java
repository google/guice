/*
 * Copyright (C) 2006 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject.spi;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Binder;
import com.google.inject.internal.ErrorId;
import com.google.inject.internal.Errors;
import com.google.inject.internal.GenericErrorDetail;
import com.google.inject.internal.GuiceInternal;
import com.google.inject.internal.util.SourceProvider;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.List;

/**
 * An error message and the context in which it occured. Messages are usually created internally by
 * Guice and its extensions. Messages can be created explicitly in a module using {@link
 * com.google.inject.Binder#addError(Throwable) addError()} statements:
 *
 * <pre>
 *     try {
 *       bindPropertiesFromFile();
 *     } catch (IOException e) {
 *       addError(e);
 *     }</pre>
 *
 * @author crazybob@google.com (Bob Lee)
 */
public final class Message implements Serializable, Element {
  private final ErrorId errorId;
  private final ErrorDetail<?> errorDetail;

  /** @since vNext */
  public Message(GuiceInternal internalOnly, ErrorId errorId, ErrorDetail<?> errorDetail) {
    checkNotNull(internalOnly);
    this.errorId = errorId;
    this.errorDetail = errorDetail;
  }

  private Message(ErrorId errorId, ErrorDetail<?> errorDetail) {
    this.errorId = errorId;
    this.errorDetail = errorDetail;
  }

  /** @since 2.0 */
  public Message(ErrorId errorId, List<Object> sources, String message, Throwable cause) {
    this.errorId = errorId;
    this.errorDetail = new GenericErrorDetail(errorId, message, sources, cause);
  }

  /** @since 2.0 */
  public Message(List<Object> sources, String message, Throwable cause) {
    this(ErrorId.OTHER, sources, message, cause);
  }

  /** @since 4.0 */
  public Message(String message, Throwable cause) {
    this(ImmutableList.of(), message, cause);
  }

  public Message(Object source, String message) {
    this(ImmutableList.of(source), message, null);
  }

  public Message(String message) {
    this(ImmutableList.of(), message, null);
  }

  /**
   * Returns details about this error message.
   *
   * @since vNext
   */
  public ErrorDetail<?> getErrorDetail() {
    return errorDetail;
  }

  @Override
  public String getSource() {
    List<Object> sources = errorDetail.getSources();
    return sources.isEmpty()
        ? SourceProvider.UNKNOWN_SOURCE.toString()
        : Errors.convert(Iterables.getLast(sources)).toString();
  }

  /** @since 2.0 */
  public List<Object> getSources() {
    return errorDetail.getSources();
  }

  /** Gets the error message text. */
  public String getMessage() {
    return errorDetail.getMessage();
  }

  /** @since 2.0 */
  @Override
  public <T> T acceptVisitor(ElementVisitor<T> visitor) {
    return visitor.visit(this);
  }

  /**
   * Returns the throwable that caused this message, or {@code null} if this message was not caused
   * by a throwable.
   *
   * @since 2.0
   */
  public Throwable getCause() {
    return errorDetail.getCause();
  }

  @Override
  public String toString() {
    return errorDetail.getMessage();
  }

  @Override
  public int hashCode() {
    return errorDetail.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Message)) {
      return false;
    }
    Message e = (Message) o;
    return errorDetail.equals(e.errorDetail);
  }

  /** @since 2.0 */
  @Override
  public void applyTo(Binder binder) {
    binder.withSource(getSource()).addError(this);
  }

  /** Returns a copy of this {@link Message} with its sources replaced. */
  public Message withSource(List<Object> newSources) {
    return new Message(errorId, errorDetail.withSources(newSources));
  }

  /**
   * When serialized, we convert the error detail to a {@link GenericErrorDetail} with string
   * sources. This hurts our formatting, but it guarantees that the receiving end will be able to
   * read the message.
   */
  private Object writeReplace() throws ObjectStreamException {
    Object[] sourcesAsStrings = getSources().toArray();
    for (int i = 0; i < sourcesAsStrings.length; i++) {
      sourcesAsStrings[i] = Errors.convert(sourcesAsStrings[i]).toString();
    }
    return new Message(
        errorId,
        new GenericErrorDetail(
            errorId, getMessage(), ImmutableList.copyOf(sourcesAsStrings), getCause()));
  }

  private static final long serialVersionUID = 0;
}
