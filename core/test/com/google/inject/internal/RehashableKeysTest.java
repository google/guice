package com.google.inject.internal;

import static com.google.inject.internal.RehashableKeys.Keys.needsRehashing;
import static com.google.inject.internal.RehashableKeys.Keys.rehash;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.BindingAnnotation;
import com.google.inject.Key;
import com.google.inject.name.Names;

import junit.framework.TestCase;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;

/**
 * @author chrispurcell@google.com (Chris Purcell)
 */
public class RehashableKeysTest extends TestCase {

  public void testNeedsRehashing_noAnnotation() {
    Key<?> key = Key.get(Integer.class);
    assertFalse(needsRehashing(key));
  }

  public void testNeedsRehashing_noParametersAnnotation() {
    Key<?> key = Key.get(Integer.class, NoParametersAnnotation.class);
    assertFalse(needsRehashing(key));
  }

  public void testNeedsRehashing_immutableAnnotation() {
    Key<?> key = Key.get(Integer.class, Names.named("testy"));
    assertFalse(needsRehashing(key));
  }

  public void testNeedsRehashing_mutableAnnotation() {
    MutableTestAnnotation annotation = new MutableTestAnnotation(100);
    Key<?> key = Key.get(Integer.class, annotation);
    assertFalse(needsRehashing(key));

    annotation.setValue(101);
    assertTrue(needsRehashing(key));

    Key<?> key2 = Key.get(Integer.class, annotation);
    assertTrue(needsRehashing(key));
    assertFalse(needsRehashing(key2));

    annotation.setValue(102);
    assertTrue(needsRehashing(key));
    assertTrue(needsRehashing(key2));

    annotation.setValue(100);
    assertFalse(needsRehashing(key));
    assertTrue(needsRehashing(key2));
  }

  public void testRehash_noParametersAnnotation() {
    Key<?> key = Key.get(Integer.class, NoParametersAnnotation.class);
    assertSame(key, rehash(key));
  }

  public void testRehash_noAnnotation() {
    Key<?> key = Key.get(Integer.class);
    assertSame(key, rehash(key));
  }

  public void testRehash_immutableAnnotation() {
    Key<?> key = Key.get(Integer.class, Names.named("testy"));
    Key<?> keyCopy = rehash(key);
    assertEquals(key, keyCopy);
    assertEquals(key.hashCode(), keyCopy.hashCode());
  }

  public void testRehash_mutableAnnotation() {
    MutableTestAnnotation annotation = new MutableTestAnnotation(100);
    Key<?> key = Key.get(Integer.class, annotation);
    Key<?> keyCopy = rehash(key);
    assertTrue(key.equals(keyCopy));
    assertTrue(key.hashCode() == keyCopy.hashCode());

    annotation.setValue(101);
    Key<?> keyCopy2 = rehash(key);
    assertTrue(key.equals(keyCopy2));
    assertFalse(key.hashCode() == keyCopy2.hashCode());

    annotation.setValue(100);
    Key<?> keyCopy3 = rehash(keyCopy2);
    assertTrue(key.equals(keyCopy3));
    assertTrue(key.hashCode() == keyCopy3.hashCode());
    assertTrue(keyCopy2.equals(keyCopy3));
    assertFalse(keyCopy2.hashCode() == keyCopy3.hashCode());
  }

  @Retention(RUNTIME) @BindingAnnotation
  private @interface NoParametersAnnotation { }

  @Retention(RUNTIME) @BindingAnnotation
  private @interface TestAnnotation {
    int value();
  }

  private static class MutableTestAnnotation implements TestAnnotation {

    private int value;

    MutableTestAnnotation(int value) {
      this.value = value;
    }

    public Class<? extends Annotation> annotationType() {
      return TestAnnotation.class;
    }

    public int value() {
      return value;
    }

    void setValue(int value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object obj) {
      return (obj instanceof TestAnnotation) && (((TestAnnotation) obj).value() == value);
    }

    @Override
    public int hashCode() {
      return value;
    }
  }
}
