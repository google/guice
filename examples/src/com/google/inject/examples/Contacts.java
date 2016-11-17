package com.google.inject.examples;

public interface Contacts {
  Iterable<Contact> findByName(String name);
}
