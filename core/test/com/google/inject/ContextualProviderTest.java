package com.google.inject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import com.google.common.collect.ImmutableMap;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ContextualProviderTest {
  @Test
  public void testContextualProvide() {
    Map<String, Person> people = ImmutableMap.of(
        "Bob", new Person("Bob"),
        "Sam", new Person("Sam")
    );
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Person.class).toContextualProvider(context -> {
          Info info = context.getAnnotation(Info.class);
          if (info != null) {
            return people.get(info.name());
          }
          throw new ProvisionException("Cannot provide Person for non-annotated " + context);
        });
      }
    });
    ClassWithContext data = injector.getInstance(ClassWithContext.class);
    assertNotNull(data.a);
    assertNotNull(data.b);
    assertNotNull(data.personFromMethod);
    assertEquals("Bob", data.a.name);
    assertEquals("Sam", data.b.name);
    assertSame(data.a, data.personFromMethod);
  }

  static class ClassWithContext {
    @Inject @Info(name = "Bob") Person a;
    @Inject @Info(name = "Sam") Person b;
    Person personFromMethod;

    @Inject
    void acceptPerson(@Info(name = "Bob") Person person) {
      personFromMethod = person;
    }
  }

  static class Person {
    final String name;

    Person(final String name) {
      this.name = name;
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.PARAMETER})
  @interface Info {
    String name();
  }
}
