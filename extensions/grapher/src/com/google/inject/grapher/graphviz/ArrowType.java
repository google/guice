/**
 * Copyright (C) 2008 Google Inc.
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

package com.google.inject.grapher.graphviz;

/**
 * Arrow symbols that are available from Graphviz. These can be composed by
 * concatenation to make double arrows and such.
 * <p>
 * See: http://www.graphviz.org/doc/info/arrows.html
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 */
public enum ArrowType {
  BOX("box"),
  BOX_OPEN("obox"),
  CROW("crow"),
  DIAMOND("diamond"),
  DIAMOND_OPEN("odiamond"),
  DOT("dot"),
  DOT_OPEN("odot"),
  INVERTED("inv"),
  INVERTED_OPEN("oinv"),
  NONE("none"),
  NORMAL("normal"),
  NORMAL_OPEN("onormal"),
  TEE("tee"),
  VEE("vee");

  private final String arrowType;

  ArrowType(String arrowType) {
    this.arrowType = arrowType;
  }

  @Override
  public String toString() {
    return arrowType;
  }
}
