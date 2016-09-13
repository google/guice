/*
 * Copyright (C) 2013 Google Inc.
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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

public class ContinuingHttpServletRequestTest extends TestCase {

  private static final String TEST_VALUE_1 = "testValue1";
  private static final String TEST_VALUE_2 = "testValue2";
  private static final int DEFAULT_MAX_AGE = new Cookie("", "").getMaxAge();

  public void testReturnNullCookiesIfDelegateHasNoNull() {
    HttpServletRequest delegate = createMock(HttpServletRequest.class);
    expect(delegate.getCookies()).andStubReturn(null);

    replay(delegate);

    assertNull(new ContinuingHttpServletRequest(delegate).getCookies());

    verify(delegate);
  }

  public void testReturnDelegateCookies() {
    Cookie[] cookies =
        new Cookie[] {new Cookie("testName1", TEST_VALUE_1), new Cookie("testName2", "testValue2")};
    HttpServletRequest delegate = createMock(HttpServletRequest.class);
    expect(delegate.getCookies()).andStubReturn(cookies);

    replay(delegate);

    ContinuingHttpServletRequest continuingRequest = new ContinuingHttpServletRequest(delegate);

    assertCookieArraysEqual(cookies, continuingRequest.getCookies());

    // Now mutate the original cookies, this shouldnt be reflected in the continued request.
    cookies[0].setValue("INVALID");
    cookies[1].setValue("INVALID");
    cookies[1].setMaxAge(123);

    try {
      assertCookieArraysEqual(cookies, continuingRequest.getCookies());
      throw new Error();
    } catch (AssertionFailedError e) {
      // Expected.
    }

    // Verify that they remain equal to the original values.
    assertEquals(TEST_VALUE_1, continuingRequest.getCookies()[0].getValue());
    assertEquals(TEST_VALUE_2, continuingRequest.getCookies()[1].getValue());
    assertEquals(DEFAULT_MAX_AGE, continuingRequest.getCookies()[1].getMaxAge());

    // Perform a snapshot of the snapshot.
    ContinuingHttpServletRequest furtherContinuingRequest =
        new ContinuingHttpServletRequest(continuingRequest);

    // The cookies should be fixed.
    assertCookieArraysEqual(continuingRequest.getCookies(), furtherContinuingRequest.getCookies());

    verify(delegate);
  }

  private static void assertCookieArraysEqual(Cookie[] one, Cookie[] two) {
    assertEquals(one.length, two.length);
    for (int i = 0; i < one.length; i++) {
      Cookie cookie = one[i];
      assertCookieEquality(cookie, two[i]);
    }
  }

  private static void assertCookieEquality(Cookie one, Cookie two) {
    assertEquals(one.getName(), two.getName());
    assertEquals(one.getComment(), two.getComment());
    assertEquals(one.getDomain(), two.getDomain());
    assertEquals(one.getPath(), two.getPath());
    assertEquals(one.getValue(), two.getValue());
    assertEquals(one.getMaxAge(), two.getMaxAge());
    assertEquals(one.getSecure(), two.getSecure());
  }
}
