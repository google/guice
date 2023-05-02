package com.google.inject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.common.collect.ImmutableMap;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
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
        bind(Person.class).toContextualProvider(point -> {
          Member member = point.getMember();
          if (member instanceof AnnotatedElement) {
            Info info = ((AnnotatedElement) member).getAnnotation(Info.class);
            if (info != null) {
              return people.get(info.name());
            }
          }
          throw new ProvisionException("Cannot provide Person for non-annotated " + member);
        });
      }
    });
    ClassWithContext data = injector.getInstance(ClassWithContext.class);
    assertNotNull(data.a);
    assertNotNull(data.b);
    assertEquals("Bob", data.a.name);
    assertEquals("Sam", data.b.name);
  }

  static class ClassWithContext {
    @Inject @Info(name = "Bob") Person a;
    @Inject @Info(name = "Sam") Person b;
  }

  static class Person {
    final String name;

    Person(final String name) {
      this.name = name;
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.FIELD)
  @interface Info {
    String name();
  }
}
