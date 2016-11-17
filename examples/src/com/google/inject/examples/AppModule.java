package com.google.inject.examples;

import com.google.inject.AbstractModule;

import java.net.URL;

/**
 * @author Adam Tomaja
 */
public class AppModule extends AbstractModule {

    private final URL xmlUrl;

    public AppModule(URL xmlUrl) {
        this.xmlUrl = xmlUrl;
    }

    @Override
    protected void configure() {
        bind(Contacts.class).to(SimCard.class);
        install(new XmlBeanModule(xmlUrl));
    }
}