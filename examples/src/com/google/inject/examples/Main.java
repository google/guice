/*
 * Copyright (C) 2007 Google Inc.
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

package com.google.inject.examples;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.examples.model.Phone;

import java.net.URL;
import java.util.logging.Logger;

/**
 * @author crazyboblee
 * @author Adam Tomaja
 */
public class Main {

    static final Logger logger = Logger.getLogger(Main.class.getName());

    public static final String XML_FILE = "phone.xml";

    public static void main(String[] args) {
        final URL xmlUrl = Main.class.getResource(XML_FILE);

        Injector injector = Guice.createInjector(new AppModule(xmlUrl));

        Phone phone = injector.getInstance(Phone.class);

        if (phone.getContacts() == null) {
            throw new AssertionError();
        } else {
            logger.info("It worked!");
        }
    }

}
