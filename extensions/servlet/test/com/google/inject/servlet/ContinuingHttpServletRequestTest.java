/**
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

import junit.framework.TestCase;

import java.util.Arrays;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

public class ContinuingHttpServletRequestTest extends TestCase {

  public void testReturnNullCookiesIfDelegateHasNoNull() {
    HttpServletRequest delegate = createMock(HttpServletRequest.class);
    expect(delegate.getCookies()).andStubReturn(null);

    replay(delegate);

    assertNull(new ContinuingHttpServletRequest(delegate).getCookies());

    verify(delegate);
  }
  
  public void testReturnDelegateCookies() {
    Cookie[] cookies = new Cookie[]{
        new Cookie("testName1", "testValue1"),
        new Cookie("testName2", "testValue2")
    };
    HttpServletRequest delegate = createMock(HttpServletRequest.class);
    expect(delegate.getCookies()).andStubReturn(cookies);

    replay(delegate);

    assertTrue(Arrays.equals(cookies,
        new ContinuingHttpServletRequest(delegate).getCookies()));

    verify(delegate);
  }
}