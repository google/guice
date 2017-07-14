/*
 * Copyright (C) 2012 Google Inc.
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

package com.google.inject.servlet;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.net.UrlEscapers;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.servlet.http.HttpServletRequest;

/**
 * Some servlet utility methods.
 *
 * @author ntang@google.com (Michael Tang)
 */
final class ServletUtils {
  private static final Splitter SLASH_SPLITTER = Splitter.on('/');
  private static final Joiner SLASH_JOINER = Joiner.on('/');

  private ServletUtils() {
    // private to prevent instantiation.
  }

  /**
   * Gets the context path relative path of the URI. Returns the path of the resource relative to
   * the context path for a request's URI, or null if no path can be extracted.
   *
   * <p>Also performs url decoding and normalization of the path.
   */
  // @Nullable
  static String getContextRelativePath(
      // @Nullable
      final HttpServletRequest request) {
    if (request != null) {
      String contextPath = request.getContextPath();
      String requestURI = request.getRequestURI();
      if (contextPath.length() < requestURI.length()) {
        String suffix = requestURI.substring(contextPath.length());
        return normalizePath(suffix);
      } else if (requestURI.trim().length() > 0 && contextPath.length() == requestURI.length()) {
        return "/";
      }
    }
    return null;
  }

  /** Normalizes a path by unescaping all safe, percent encoded characters. */
  static String normalizePath(String path) {
    StringBuilder sb = new StringBuilder(path.length());
    int queryStart = path.indexOf('?');
    String query = null;
    if (queryStart != -1) {
      query = path.substring(queryStart);
      path = path.substring(0, queryStart);
    }
    // Normalize the path.  we need to decode path segments, normalize and rejoin in order to
    // 1. decode and normalize safe percent escaped characters.  e.g. %70 -> 'p'
    // 2. decode and interpret dangerous character sequences. e.g. /%2E/ -> '/./' -> '/'
    // 3. preserve dangerous encoded characters. e.g. '/%2F/' -> '///' -> '/%2F'
    List<String> segments = new ArrayList<>();
    for (String segment : SLASH_SPLITTER.split(path)) {
      // This decodes all non-special characters from the path segment.  so if someone passes
      // /%2E/foo we will normalize it to /./foo and then /foo
      String normalized =
          UrlEscapers.urlPathSegmentEscaper().escape(lenientDecode(segment, UTF_8, false));
      if (".".equals(normalized)) {
        // skip
      } else if ("..".equals(normalized)) {
        if (segments.size() > 1) {
          segments.remove(segments.size() - 1);
        }
      } else {
        segments.add(normalized);
      }
    }
    SLASH_JOINER.appendTo(sb, segments);
    if (query != null) {
      sb.append(query);
    }
    return sb.toString();
  }


  /**
   * Percent-decodes a US-ASCII string into a Unicode string. The specified encoding is used to
   * determine what characters are represented by any consecutive sequences of the form
   * "%<i>XX</i>". This is the lenient kind of decoding that will simply ignore and copy as-is any
   * "%XX" sequence that is invalid (for example, "%HH").
   *
   * @param string a percent-encoded US-ASCII string
   * @param encoding a character encoding
   * @param decodePlus boolean to indicate whether to decode '+' as ' '
   * @return a Unicode string
   */
  private static String lenientDecode(String string, Charset encoding, boolean decodePlus) {

    checkNotNull(string);
    checkNotNull(encoding);

    if (decodePlus) {
      string = string.replace('+', ' ');
    }

    int firstPercentPos = string.indexOf('%');

    if (firstPercentPos < 0) {
      return string;
    }

    ByteAccumulator accumulator = new ByteAccumulator(string.length(), encoding);
    StringBuilder builder = new StringBuilder(string.length());

    if (firstPercentPos > 0) {
      builder.append(string, 0, firstPercentPos);
    }

    for (int srcPos = firstPercentPos; srcPos < string.length(); srcPos++) {

      char c = string.charAt(srcPos);

      if (c < 0x80) { // ASCII
        boolean processed = false;

        if (c == '%' && string.length() >= srcPos + 3) {
          String hex = string.substring(srcPos + 1, srcPos + 3);

          try {
            int encoded = Integer.parseInt(hex, 16);

            if (encoded >= 0) {
              accumulator.append((byte) encoded);
              srcPos += 2;
              processed = true;
            }
          } catch (NumberFormatException ignore) {
            // Expected case (badly formatted % group)
          }
        }

        if (!processed) {
          if (accumulator.isEmpty()) {
            // We're not accumulating elements of a multibyte encoded
            // char, so just toss it right into the result string.

            builder.append(c);
          } else {
            accumulator.append((byte) c);
          }
        }
      } else { // Non-ASCII
        // A non-ASCII char marks the end of a multi-char encoding sequence,
        // if one is in progress.

        accumulator.dumpTo(builder);
        builder.append(c);
      }
    }

    accumulator.dumpTo(builder);

    return builder.toString();
  }

  /** Accumulates byte sequences while decoding strings, and encodes them into a StringBuilder. */
  private static class ByteAccumulator {
    private byte[] bytes;
    private int length;
    private final Charset encoding;

    ByteAccumulator(int capacity, Charset encoding) {
      this.bytes = new byte[Math.min(16, capacity)];
      this.encoding = encoding;
    }

    void append(byte b) {
      ensureCapacity(length + 1);
      bytes[length++] = b;
    }

    void dumpTo(StringBuilder dest) {
      if (length != 0) {
        dest.append(new String(bytes, 0, length, encoding));
        length = 0;
      }
    }

    boolean isEmpty() {
      return length == 0;
    }

    private void ensureCapacity(int minCapacity) {
      int cap = bytes.length;
      if (cap >= minCapacity) {
        return;
      }
      int newCapacity = cap + (cap >> 1); // *1.5
      if (newCapacity < minCapacity) {
        // we are close to overflowing, grow by smaller steps
        newCapacity = minCapacity;
      }
      // in other cases, we will naturally throw an OOM from here
      bytes = Arrays.copyOf(bytes, newCapacity);
    }
  }

}
