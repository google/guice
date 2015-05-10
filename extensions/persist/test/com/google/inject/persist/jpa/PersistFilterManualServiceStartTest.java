/**
 * Copyright (C) 2010 Google, Inc.
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;

import junit.framework.TestCase;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.persist.PersistFilter;
import com.google.inject.persist.UnitOfWork;


public class PersistFilterManualServiceStartTest extends TestCase {
    private Injector injector;

    
    private FilterConfig filterConfig;
    private PersistFilter persistFilter;
    private JpaPersistService sut;

    @Override
    public void setUp() {
        
        injector = Guice.createInjector(new JpaPersistModule("testUnit"));
        sut = injector.getInstance(JpaPersistService.class);
        persistFilter = new PersistFilter(injector.getInstance(UnitOfWork.class), sut);
        filterConfig = mock(FilterConfig.class);
        //persistFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
    }

    @Override
    public final void tearDown() {
    }

    public void testFilterConfigurationWithManualServiceStartPositive() throws ServletException, IOException {
        when(filterConfig.getInitParameter("startPersistanceServiceManually")).thenReturn("true");
        sut.start();
        persistFilter.init(filterConfig);
        assertPersistServiceStateAndStartIt(true);
        persistFilter.doFilter(null, null, mock(FilterChain.class));
        persistFilter.destroy();
    }
    
    public void testFilterConfigurationWithManualServiceStartNegative() throws ServletException, IOException {
        when(filterConfig.getInitParameter("startPersistanceServiceManually")).thenReturn("true");
        persistFilter.init(filterConfig);
        assertPersistServiceStateAndStartIt(false);
        persistFilter.doFilter(null, null, mock(FilterChain.class));
        // Not calling persist filter destroy method, because persistence service is not supposed to be initialized
    }

    public void testFilterConfigurationWithAutomaticServiceStartPositive() throws ServletException, IOException {
        persistFilter.init(filterConfig);
        assertPersistServiceStateAndStartIt(true);
        persistFilter.doFilter(null, null, mock(FilterChain.class));
        persistFilter.destroy();
    }
    
    private void assertPersistServiceStateAndStartIt(boolean isStarted) {
        try {
            sut.start();
            assertTrue(!isStarted);
        } catch (IllegalStateException e) {
            assertTrue(isStarted);
        }
    }
        
}
