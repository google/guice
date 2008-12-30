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

package com.google.inject.grapher.demo;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.google.inject.grapher.GrapherModule;
import com.google.inject.grapher.InjectorGrapher;
import com.google.inject.grapher.graphviz.GraphvizModule;
import com.google.inject.grapher.graphviz.GraphvizRenderer;

import java.io.File;
import java.io.PrintWriter;

/**
 * Application that instantiates {@link BackToTheFutureModule} and graphs it,
 * writing the output to a DOT-formatted file (filename specified on the
 * command line).
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 */
public class InjectorGrapherDemo {
  public static void main(String[] args) throws Exception {
    // TODO(phopkins): Switch to Stage.TOOL when issue 297 is fixed.
    Injector demoInjector = Guice.createInjector(Stage.DEVELOPMENT,
        new BackToTheFutureModule(), new MultibinderModule());
    PrintWriter out = new PrintWriter(new File(args[0]), "UTF-8");

    Injector injector = Guice.createInjector(new GrapherModule(), new GraphvizModule());
    GraphvizRenderer renderer = injector.getInstance(GraphvizRenderer.class);
    renderer.setOut(out).setRankdir("TB");

    injector.getInstance(InjectorGrapher.class)
        .of(demoInjector)
        .graph();
  }
}
