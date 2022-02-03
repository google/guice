package com.example;

import static org.junit.Assert.assertEquals;

import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit test for simple App. */
@RunWith(JUnit4.class)
public final class AppTest {
  private final List<String> messages = new ArrayList<>();

  @Bind @App.Message private final String message = "hello, test";

  @Bind
  private final Printer printer =
      new Printer() {
        @Override
        public void printMessage(String message) {
          messages.add(message);
        }
      };

  @Inject private App app;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void run_printsMessage() {
    app.run();

    assertEquals(1, messages.size());
    assertEquals(message, messages.get(0));
  }
}
