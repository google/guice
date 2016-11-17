package com.google.inject.examples;

public class Phone {

  Contacts contacts;

  public void setContacts(Contacts contacts) {
    this.contacts = contacts;
  }

  public Contacts getContacts() {
    return contacts;
  }
}
