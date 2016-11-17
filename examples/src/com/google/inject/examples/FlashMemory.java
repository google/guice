package com.google.inject.examples;

import java.util.Collections;

public class FlashMemory implements Contacts {

  public Iterable<Contact> findByName(String name) {
    return Collections.emptyList();
  }
}
