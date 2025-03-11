/*
 * Copyright (C) 2024 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.inject.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.inject.Key;
import com.google.inject.spi.Dependency;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class InternalContextTest {
  private static final Dependency<String> DEP = Dependency.get(Key.get(String.class));
  private static final Dependency<Runnable> PROXYABLE_DEP = Dependency.get(Key.get(Runnable.class));

  @Test
  public void testWithoutProxySupport_throwsBecauseWeCannotProxy()
      throws InternalProvisionException {
    InternalContext.WithoutProxySupport context =
        new InternalContext.WithoutProxySupport(new Object[1]);
    context.tryStartConstruction(1, PROXYABLE_DEP);
    InternalProvisionException ipe =
        assertThrows(
            InternalProvisionException.class, () -> context.tryStartConstruction(1, PROXYABLE_DEP));
    assertThat(ipe.toProvisionException())
        .hasMessageThat()
        .contains("circular dependencies are disabled");
  }

  // Force a hash collision in the internal context table.
  @Test
  public void testWithoutProxySupport_forceHashCollision() throws InternalProvisionException {
    // Initial table size is 16, so we should be able to to force hash collisions using keys that
    // are === mod 16
    InternalContext.WithoutProxySupport context =
        new InternalContext.WithoutProxySupport(new Object[1]);
    context.insert(1, DEP);
    assertThat(context.get(1)).isEqualTo(1);
    context.insert(17, DEP);
    assertThat(context.get(17)).isEqualTo(2);
    context.insert(2, DEP);
    assertThat(context.get(2)).isEqualTo(3);
    context.insert(18, DEP);
    assertThat(context.get(18)).isEqualTo(4);
    context.remove(1); // This should trigger a shift
    assertThat(context.get(1)).isEqualTo(-1);
    assertThat(context.get(17)).isEqualTo(1);
    assertThat(context.get(2)).isEqualTo(2);
    assertThat(context.get(18)).isEqualTo(3);
    context.remove(2); // This should trigger a shift
    assertThat(context.get(1)).isEqualTo(-1);
    assertThat(context.get(17)).isEqualTo(1);
    assertThat(context.get(2)).isEqualTo(-1);
    assertThat(context.get(18)).isEqualTo(2);
  }

  @Test
  public void testWithProxySupport_throwsWhenWeCannotProxy() throws InternalProvisionException {
    InternalContext.WithProxySupport context = new InternalContext.WithProxySupport(new Object[1]);
    context.tryStartConstruction(1, DEP);
    // cannot proxy a String
    InternalProvisionException ipe =
        assertThrows(InternalProvisionException.class, () -> context.tryStartConstruction(1, DEP));
    assertThat(ipe.toProvisionException()).hasMessageThat().contains("but it is not an interface");
  }

  @Test
  public void testWithProxySupport_returnsProxyWhenWeCan() throws InternalProvisionException {
    InternalContext.WithProxySupport context = new InternalContext.WithProxySupport(new Object[1]);
    assertThat(context.tryStartConstruction(1, PROXYABLE_DEP)).isNull();
    Runnable proxy = context.tryStartConstruction(1, PROXYABLE_DEP);
    Runnable proxy2 = context.tryStartConstruction(1, PROXYABLE_DEP);
    assertThat(BytecodeGen.isCircularProxy(proxy)).isTrue();
    int[] called = new int[1];
    context.finishConstruction(1, (Runnable) () -> called[0]++);
    proxy.run(); // The proxy should call the real thing
    assertThat(called[0]).isEqualTo(1);
    proxy2.run(); // The proxy should call the real thing
    assertThat(called[0]).isEqualTo(2);
  }

  @Test
  public void testWithProxySupport_multipleCollidingProxiesAcrossAResize()
      throws InternalProvisionException {
    InternalContext.WithProxySupport context = new InternalContext.WithProxySupport(new Object[1]);
    // Initial table size is 16, so we should be able to to force hash collisions using keys that
    // are === mod 16
    assertThat(context.tryStartConstruction(1, PROXYABLE_DEP)).isNull();
    assertThat(context.get(1)).isEqualTo(1);
    assertThat(context.tryStartConstruction(17, PROXYABLE_DEP)).isNull();
    assertThat(context.get(17)).isEqualTo(2);
    // this will also collide with 1 after a resize
    assertThat(context.tryStartConstruction(33, PROXYABLE_DEP)).isNull();
    assertThat(context.get(33)).isEqualTo(3);
    Runnable proxy1 = context.tryStartConstruction(1, PROXYABLE_DEP);
    Runnable proxy2 = context.tryStartConstruction(17, PROXYABLE_DEP);
    Runnable proxy3 = context.tryStartConstruction(33, PROXYABLE_DEP);
    assertThat(BytecodeGen.isCircularProxy(proxy1)).isTrue();
    assertThat(BytecodeGen.isCircularProxy(proxy2)).isTrue();
    assertThat(BytecodeGen.isCircularProxy(proxy3)).isTrue();
    // Now force the table to resize.  It resizes to 32 after it is 2/3 full.
    for (int i = 2; i < 12; i++) {
      assertThat(context.tryStartConstruction(i, DEP)).isNull();
    }
    // Check where our original keys moved
    assertThat(context.get(1)).isEqualTo(1);
    assertThat(context.get(17)).isEqualTo(17); // no longer a collision
    assertThat(context.get(33)).isEqualTo(2); // still a collision with 1

    // Now complete the construction of the proxies and check that they call the right things.
    int[] called = new int[3];
    context.finishConstruction(1, (Runnable) () -> called[0] = 1);
    context.finishConstruction(17, (Runnable) () -> called[1] = 17);
    context.finishConstruction(33, (Runnable) () -> called[2] = 33);
    proxy1.run();
    assertThat(called).asList().containsExactly(1, 0, 0).inOrder();
    proxy2.run();
    assertThat(called).asList().containsExactly(1, 17, 0).inOrder();
    proxy3.run();
    assertThat(called).asList().containsExactly(1, 17, 33).inOrder();
  }

  // Force a hash collision in the internal context table.
  @Test
  public void testWithProxySupport_forceHashCollision() throws InternalProvisionException {
    // Initial table size is 16, so we should be able to to force hash collisions using keys that
    // are === mod 16
    InternalContext.WithProxySupport context = new InternalContext.WithProxySupport(new Object[1]);
    context.insert(1, DEP, null);
    assertThat(context.get(1)).isEqualTo(1);
    context.insert(17, DEP, null);
    assertThat(context.get(17)).isEqualTo(2);
    context.insert(2, DEP, null);
    assertThat(context.get(2)).isEqualTo(3);
    context.insert(18, DEP, null);
    assertThat(context.get(18)).isEqualTo(4);
    context.remove(1); // This should trigger a shift
    assertThat(context.get(1)).isEqualTo(-1);
    assertThat(context.get(17)).isEqualTo(1);
    assertThat(context.get(2)).isEqualTo(2);
    assertThat(context.get(18)).isEqualTo(3);
    context.remove(2); // This should trigger a shift
    assertThat(context.get(1)).isEqualTo(-1);
    assertThat(context.get(17)).isEqualTo(1);
    assertThat(context.get(2)).isEqualTo(-1);
    assertThat(context.get(18)).isEqualTo(2);
  }

  /**
   * Insert a bunch of keys and check that our 2 table implementations are storing them in the same
   * place.
   *
   * <p>While the code is somewhat duplicated we can at least ensure that the two implementations
   * are consistent.
   */
  @Test
  public void testFuzzTables() throws InternalProvisionException {
    int[] keys = IntStream.range(1, 1024 * 1024).toArray();

    Random rnd = new Random(12345);
    for (int i = 0; i < 30; i++) {
      InternalContext.WithoutProxySupport withoutProxySupport =
          new InternalContext.WithoutProxySupport(new Object[1]);
      InternalContext.WithProxySupport withProxySupport =
          new InternalContext.WithProxySupport(new Object[1]);
      for (int key : keys) {
        // insert throws on duplicates... so no need to check return values
        withoutProxySupport.insert(key, DEP);
        withProxySupport.insert(key, DEP, null);
        // They should have stored them in the same place.
        assertThat(withoutProxySupport.get(key)).isEqualTo(withProxySupport.get(key));
      }

      // Remove in a different order than how we inserted them.
      for (int key : shuffleArray(keys.clone(), rnd)) {
        // remove throws if we cannot find the key... so no need to check return values
        withoutProxySupport.remove(key);
        withProxySupport.remove(key);
      }
      // shuffle the keys so we insert in a different order next time, this ensures that we will
      // trigger some collisions
      shuffleArray(keys, rnd);
    }
  }

  private static int[] shuffleArray(int[] arr, Random rnd) {
    for (int i = arr.length - 1; i > 0; i--) {
      int index = rnd.nextInt(i + 1);
      // Simple swap
      int a = arr[index];
      arr[index] = arr[i];
      arr[i] = a;
    }
    return arr;
  }
}
