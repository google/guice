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
 *
 */
package com.google.inject.servlet;

import com.google.inject.Key;
import com.google.inject.Module;
import java.util.Map;
import javax.servlet.Filter;
import javax.servlet.http.HttpServlet;

/**
 * <p> Use this utility class to configure your guice-servlet module. This is equivalent to
 * installing the {@link ServletModule}. Do *not* install the servlet module AND this system.
 * Always prefer this mechanism unless you are working with a legacy Guice 1.0 configuration.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 * @see com.google.inject.servlet.GuiceFilter Configuring the WebFilter
 */
public final class Servlets {
  private Servlets() {
  }

  /**
   * <h3>Servlet Mapping EDSL</h3>
   *
   * <p> Part of the EDSL builder language for configuring servlets
   * and filters with guice-servlet. Think of this as an in-code replacement for web.xml.
   * Filters and servlets are configured here using simple java method calls. Here is a typical
   * example of registering a filter when creating your Guice injector:
   *
   * <pre>
   *   Guice.createInjector(..., Servlets.configure()
   *      .filters()
   *      .servlets()
   *          <b>.serve("*.html").with(MyServlet.class)</b>
   *
   *      .buildModule();
   * </pre>
   *
   * This registers a servlet (subclass of {@code HttpServlet}) called {@code MyServlet} to service
   * any web pages ending in {@code .html}. You can also use a path-style syntax to register
   * servlets:
   *
   * <pre>
   *          <b>.serve("/my/*").with(MyServlet.class)</b>
   * </pre>
   *
   * You are free to register as many servlets and filters as you like this way:
   *
   * <pre>
   *   Guice.createInjector(..., Servlets.configure()
   *      .filters()
   *          .filter("/*").through(MyFilter.class)
   *          .filter("*.css").through(MyCssFilter.class)
   *          //etc..
   *
   *      .servlets()
   *          .serve("*.html").with(MyServlet.class)
   *          .serve("/my/*").with(MyServlet.class)
   *          //etc..
   *
   *      .buildModule();
   * </pre>
   *
   * You can also map servlets (or filters) to URIs using regular expressions:
   * <pre>
   *      .servlets()
   *          <b>.serveRegex("(.)*ajax(.)*").with(MyAjaxServlet.class)</b>
   * </pre>
   *
   * This will map any URI containing the text "ajax" in it to {@code MyAjaxServlet}. Such as:
   * <ul>
   * <li>http://www.google.com/ajax.html</li>
   * <li>http://www.google.com/content/ajax/index</li>
   * <li>http://www.google.com/it/is_totally_ajaxian</li>
   * </ul>
   *
   * </p>
   *
   * <h3>Initialization Parameters</h3>
   * Servlets (and filters) allow you to pass in init params
   * using the {@code <init-param>} tag in web.xml. You can similarly pass in parameters to
   * Servlets and filters registered in Guice-servlet using a {@link java.util.Map} of parameter
   * name/value pairs. For example, to initialize {@code MyServlet} with two parameters
   * (name="Dhanji", site="google.com") you could write:
   *
   * <pre>
   *  Map&lt;String, String&gt; params = new HashMap&lt;String, String&gt;();
   *  params.put("name", "Dhanji");
   *  params.put("site", "google.com");
   *
   *  ...
   *      .servlets()
   *          .serve("/*").with(MyServlet.class, <b>params</b>)
   * </pre>
   *
   *
   * <h3>Binding Keys</h3>
   *
   * <p> You can also bind keys rather than classes. This lets you hide
   * implementations with package-local visbility and expose them using
   * only a Guice module and an annotation:
   *
   * <pre>
   *
   *      ...
   *      .filters()
   *          .filter("/*").through(<b>Key.get(Filter.class, Fave.class)</b>);
   * </pre>
   *
   * Where {@code Filter.class} refers to the Servlet API interface and {@code Fave.class} is a
   * custom binding annotation. Elsewhere (in one of your own modules) you can bind this
   * filter's implementation:
   *
   * <pre>
   *   bind(Filter.class)<b>.annotatedWith(Fave.class)</b>.to(MyFilterImpl.class);
   * </pre>
   *
   * See Guice documentation for more information on binding annotations.
   *
   * @return Returns the next step in the EDSL chain.
   * @see com.google.inject.servlet.GuiceFilter Configuring the WebFilter
   */
  public static WebComponentBindingBuilder configure() {
    return new WebComponentBindingBuilder() {
      public FilterBindingBuilder filters() {
        return new FiltersModuleBuilder();
      }
    };
  }

  public static interface WebComponentBindingBuilder {
      FilterBindingBuilder filters();
  }

  public static interface FilterBindingBuilder {

    /**
     * @param urlPattern Any Servlet-style pattern. examples: /*, /html/*, *.html, etc.
     * @see Servlets#configure() Configuring Guice Servlet.
     */
    FilterKeyBindingBuilder filter(String urlPattern);

    /**
     * @param regex Any Java-style regular expression.
     * @see Servlets#configure() Configuring Guice Servlet.
     */
    FilterKeyBindingBuilder filterRegex(String regex);

    /**
     * @see Servlets#configure() Configuring Guice Servlet.
     */
    ServletBindingBuilder servlets();

    /**
     * @return Returns a fully prepared Guice module to be used in
     *  {@link com.google.inject.Guice#createInjector(com.google.inject.Module[])}.
     * @see Servlets#configure() Configuring Guice Servlet.
     */
    Module buildModule();

    public static interface FilterKeyBindingBuilder {
      FilterBindingBuilder through(Class<? extends Filter> filterKey);
      FilterBindingBuilder through(Key<? extends Filter> filterKey);
      FilterBindingBuilder through(Class<? extends Filter> dummyFilterClass,
          Map<String, String> contextParams);
      FilterBindingBuilder through(Key<? extends Filter> dummyFilterClass,
          Map<String, String> contextParams);
    }
  }

  public static interface ServletBindingBuilder {
    /**
     * @param urlPattern A servlet-style URL pattern ({@code /*, *.html}, etc.)
     * @see Servlets#configure() Configuring Guice Servlet.
     */
    ServletKeyBindingBuilder serve(String urlPattern);

    /**
     * @param regex A Java-style regular expression.
     * @see Servlets#configure() Configuring Guice Servlet.
     */
    ServletKeyBindingBuilder serveRegex(String regex);


    /**
     * @return Returns configured Guice Module. Use install() or pass into createInjector().
     * @see Servlets#configure() Configuring Guice Servlet.
     */
    Module buildModule();

    public static interface ServletKeyBindingBuilder {
      ServletBindingBuilder with(Class<? extends HttpServlet> servletKey);
      ServletBindingBuilder with(Key<? extends HttpServlet> servletKey);
      ServletBindingBuilder with(Class<? extends HttpServlet> servletKey,
          Map<String, String> contextParams);
      ServletBindingBuilder with(Key<? extends HttpServlet> servletKey,
          Map<String, String> contextParams);
    }
  }
}
