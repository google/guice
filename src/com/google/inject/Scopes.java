// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject;

/**
 * Scope constants.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class Scopes {

  /**
   * Default scope's name. One instance per injection.
   */
  public static final String DEFAULT = Key.DEFAULT_NAME;

  /**
   * Singleton scope's name. One instance per {@link Container}.
   */
  public static final String SINGLETON = "singleton";
}