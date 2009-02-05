package example.xml;

public interface Contacts {
  Iterable<Contact> findByName(String name);
}
