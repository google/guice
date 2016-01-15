/**
 * Copyright (C) 2016 Google, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.inject.persist.junit;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

/**
 * <p>{@link org.junit.runner.Runner Runner} that Guicifies the test, installs
 * and manages guice persist.</p>
 * <p>Compatible with JUnit 4.4 and higher. <b>MUST</b> be used with
 * {@link PersistJUnit4Context}.</p>
 * Adds following behavior:
 * <ul>
 * <li>
 * Guicifies the test.
 * <li>
 * Initializes the JpaPersistModule.
 * <li>
 * Initializes any other Modules provided in the {@link PersistJUnit4Context#modules()}.
 * </ul>
 * Simply apply this annotation when using {@link org.junit.runner.RunWith @RunWith}. For Example:
 * <pre>
 * &#064;RunWith(PersistJUnit4ClassRunner.class)
 * &#064;PersistJUnit4Context(
 *    jpaUnit = &quot;testUnit&quot;,
 *    jpaPropertiesClass = SampleJpaProperties.class,
 *    modules = {SampleModule.class})
 * public class SomeJUnit4Test {
 *
 *  &#064;Inject
 *  private SomeInjection injected;
 *
 *  &#064;Test
 *  public void assertInjection() {
 *    Assert.assertNotNull(this.injected);
 *  }
 * }
 * </pre>
 * <p><b>Note:</b> There is no need to create an {@link Injector}.</p>
 *
 * @author jccarrillo@acm.org (JC Carrillo)
 */
public class PersistJUnit4ClassRunner extends BlockJUnit4ClassRunner {

	/**
	 * This runner's injector.
	 */
	@Inject
	private Injector injector;

	/**
	 * The custom {@link RunListener}.
	 */
	@Inject
	private PersistJUnit4RunListener persistRunListener;

	/**
	 * @param clazz the test.
	 * @throws InitializationError if something goes wrong.
	 */
	public PersistJUnit4ClassRunner(final Class<?> clazz)
			throws InitializationError {
		super(clazz);
		Guice.createInjector(new PersistJUnit4Module(clazz)).injectMembers(this);
	}

	/**
	 * Adds the {@link PersistJUnit4Module} to the notifier.
	 *
	 * @param notifier
	 */
	@Override
	public void run(RunNotifier notifier) {
		notifier.addListener(this.persistRunListener);

		super.run(notifier);
	}

	/**
	 * Injects members into test object.
	 *
	 * @return
	 * @throws Exception
	 */
	@Override
	public final Object createTest() throws Exception {
		final Object obj = super.createTest();

		this.injector.injectMembers(obj);

		return obj;
	}
}