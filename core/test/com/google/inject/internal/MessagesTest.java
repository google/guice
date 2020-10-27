package com.google.inject.internal;

import static com.google.common.testing.SerializableTester.reserialize;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.inject.ProvisionException;
import com.google.inject.spi.ErrorDetail;
import com.google.inject.spi.Message;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class MessagesTest {
  static class ExampleErrorDetail extends ErrorDetail<ExampleErrorDetail> {
    ExampleErrorDetail(String message) {
      super(message, ImmutableList.of(), null);
    }

    @Override
    public void formatDetail(List<ErrorDetail<?>> mergeableErrors, Formatter formatter) {
      formatter.format("Duplicate count: %s%n", mergeableErrors.size() + 1);
    }

    @Override
    public boolean isMergeable(ErrorDetail<?> otherError) {
      return otherError instanceof ExampleErrorDetail
          && otherError.getMessage().equals(getMessage());
    }

    @Override
    public ExampleErrorDetail withSources(List<Object> unused) {
      return new ExampleErrorDetail(getMessage());
    }
  }

  @Test
  public void customErrorMessage() {
    List<Message> messages = new ArrayList<>();
    Throwable cause = null;
    messages.add(new Message("example", cause));
    messages.add(exampleError("a"));
    messages.add(exampleError("b"));
    messages.add(exampleError("a"));

    String result = Messages.formatMessages("Example", messages);

    assertThat(result)
        .isEqualTo(
            "Example:\n\n"
                + "1) example\n\n"
                + "2) a\n"
                + "Duplicate count: 2\n\n"
                + "3) b\n"
                + "Duplicate count: 1\n\n"
                + "3 errors");
  }

  @Test
  public void provisionExceptionWithCustomErrorMessageIsSerializable() {
    Throwable cause = null;
    ProvisionException exception =
        new ProvisionException(
            ImmutableList.of(exampleError("Custom error"), new Message("Generic error", cause)));
    assertThat(reserialize(exception))
        .hasMessageThat()
        .isEqualTo(
            "Unable to provision, see the following errors:\n\n"
                + "1) Custom error\n\n"
                + "2) Generic error\n\n"
                + "2 errors");
  }

  private static Message exampleError(String message) {
    return new Message(
        GuiceInternal.GUICE_INTERNAL, ErrorId.OTHER, new ExampleErrorDetail(message));
  }
}
