/*
 * Copyright (C) 2023 Google, Inc.
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

package com.google.inject.persist.jpa;

/**
 * Options that configure how the JPA persist service will work.
 *
 * @since 6.0
 */
public final class JpaPersistOptions {

  private final boolean autoBeginWorkOnEntityManagerCreation;

  private JpaPersistOptions(JpaPersistOptions.Builder builder) {
    this.autoBeginWorkOnEntityManagerCreation = builder.autoBeginWorkOnEntityManagerCreation;
  }

  /**
   * Returns true if the work unit should automatically begin when the EntityManager is created, if
   * it hasn't already begun.
   *
   * <p>This defaults to <b>false</b> because it's not safe, as careless usage can lead to leaking
   * sessions.
   */
  public boolean getAutoBeginWorkOnEntityManagerCreation() {
    return autoBeginWorkOnEntityManagerCreation;
  }

  /** Returns a builder to set options. */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * A builder to create the options.
   *
   * @since 6.0
   */
  public static final class Builder {
    private boolean autoBeginWorkOnEntityManagerCreation;

    private Builder() {}

    public JpaPersistOptions build() {
      return new JpaPersistOptions(this);
    }

    /** Sets the {@link JpaPersistOptions#getAutoBeginWorkOnEntityManagerCreation} property. */
    public Builder setAutoBeginWorkOnEntityManagerCreation(
        boolean autoBeginWorkOnEntityManagerCreation) {
      this.autoBeginWorkOnEntityManagerCreation = autoBeginWorkOnEntityManagerCreation;
      return this;
    }
  }
}
