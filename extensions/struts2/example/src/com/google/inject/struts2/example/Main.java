/**
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

package com.google.inject.struts2.example;

import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.webapp.WebAppContext;

/**
 * Starts the example web server on port 8080. Run from "./struts2/example".
 */
public class Main {

  public static void main(String[] args) throws Exception {
    Server server = new Server();

    Connector connector = new SelectChannelConnector();
    connector.setPort(8080);
    server.setConnectors(new Connector[] { connector });

    WebAppContext webapp = new WebAppContext("./root", "/example");
    server.addHandler(webapp);

    server.start();
    server.join();
  }
}
