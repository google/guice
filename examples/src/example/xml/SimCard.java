package example.xml;

import java.util.Collections;

public class SimCard implements Contacts {

  public Iterable<Contact> findByName(String name) {
    return Collections.emptyList();
  }
}