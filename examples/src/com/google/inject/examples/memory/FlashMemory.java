package com.google.inject.examples.memory;

import com.google.inject.examples.Contacts;
import com.google.inject.examples.model.Contact;

import java.util.Collections;

public class FlashMemory implements Contacts {

  public Iterable<Contact> findByName(String name) {
    return Collections.emptyList();
  }
}
