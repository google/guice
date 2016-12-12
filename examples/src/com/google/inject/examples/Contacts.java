package com.google.inject.examples;

import com.google.inject.examples.model.Contact;

public interface Contacts {
  Iterable<Contact> findByName(String name);
}
