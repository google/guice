package com.google.inject.spi;

import com.google.common.collect.Lists;
import java.util.List;
import junit.framework.TestCase;

/** Tests for {@link Message}. */
public class MessageTest extends TestCase {

  public void testMessageHashCodeVariesWithSource() {
    String innerMessage = "This is the message.";
    Message firstMessage = new Message(1, innerMessage);
    Message secondMessage = new Message(2, innerMessage);
    assertFalse(firstMessage.hashCode() == secondMessage.hashCode());
  }

  public void testMessageHashCodeVariesWithCause() {
    String innerMessage = "This is the message.";
    List<Object> sourceList = Lists.newArrayList(new Object());
    // the throwable argument of each Message below do not have value equality
    Message firstMessage = new Message(sourceList, innerMessage, new Exception(innerMessage));
    Message secondMessage = new Message(sourceList, innerMessage, new Exception(innerMessage));
    assertFalse(firstMessage.hashCode() == secondMessage.hashCode());
  }
}
