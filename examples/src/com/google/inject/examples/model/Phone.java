package com.google.inject.examples.model;

import com.google.inject.examples.Contacts;

public class Phone {

  Contacts contacts;

  public void setContacts(Contacts contacts) {
    this.contacts = contacts;
  }

  public Contacts getContacts() {
    return contacts;
  }
}
