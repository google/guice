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
package com.google.inject.testing.persist;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.persist.jpa.JpaPersistModule;

import java.util.Map;

import static com.google.common.base.Preconditions.checkState;

/**
 * JUnit 4 JPA provider for guice persist.
 * <p>
 * Installs the {@link com.google.inject.persist.jpa.JpaPersistModule} as well as
 * all Modules found in {@link PersistJUnit4Context#modules()}.
 * </p>
 *
 * @author jccarrillo@acm.org (JC Carrillo)
 */
public class PersistJUnit4Module extends AbstractModule {

	/**
	 * The test class.
	 */
	private final Class<?> testClass;

	/**
	 * @param testClass the test class
	 */
	public PersistJUnit4Module(final Class<?> testClass) {
		super();
		checkState(null != testClass, "Invalid test class");
		this.testClass = testClass;
	}

	@Override
	protected void configure() {
		configure(PersistJUnit4Context.PersistJUnit4ContextHelper.getPersistJUnit4Context(this.testClass));
	}

	/**
	 * Configures this module.
	 *
	 * @param persistJUnit4Context the annotation from the test class
	 */
	private void configure(final PersistJUnit4Context persistJUnit4Context) {
		installModules(persistJUnit4Context);
		install(getJpaPersistModule(persistJUnit4Context));
		bind(PersistJUnit4RunListener.class).in(Singleton.class);
	}

	/**
	 * Installs all {@link Module} classes found in {@link PersistJUnit4Context#modules()}.
	 *
	 * @param persistJUnit4Context the annotation from the test class
	 */
	private void installModules(final PersistJUnit4Context persistJUnit4Context) {
		for (final Class<? extends Module> module : persistJUnit4Context.modules()) {
			try {
				install(module.newInstance());
			} catch (IllegalAccessException e) {
				throw new IllegalArgumentException(e.getMessage(), e);
			} catch (InstantiationException e) {
				throw new IllegalArgumentException(e.getMessage(), e);
			}
		}
	}

	/**
	 * Gets an instance of {@link JpaPersistModule} using values from the {@link PersistJUnit4Context}.
	 *
	 * @param persistJUnit4Context the annotation of the test class
	 * @return the {@link JpaPersistModule}
	 */
	private JpaPersistModule getJpaPersistModule(final PersistJUnit4Context persistJUnit4Context) {
		final JpaPersistModule jpaPersistModule = new JpaPersistModule(persistJUnit4Context.jpaUnit());
		jpaPersistModule.properties(getProperties(persistJUnit4Context));

		return jpaPersistModule;
	}

	/**
	 * Gets the JPA properties from the {@link PersistJUnit4Context} annotation.
	 *
	 * @param persistJUnit4Context the annotation
	 * @return the JPA properties
	 */
	private Map<?, ?> getProperties(final PersistJUnit4Context persistJUnit4Context) {
		try {
			return persistJUnit4Context.jpaPropertiesClass().newInstance().getProperties();
		} catch (InstantiationException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}
	}
}