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

import com.google.inject.Injector;
import com.google.inject.Module;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

/**
 * Apply this annotation when using {@link PersistJUnit4ClassRunner}.
 * <p>Contributes configuration information to {@link PersistJUnit4Module}
 * to help Guicify the test. Simply apply this annotation when using
 * {@link PersistJUnit4ClassRunner}.</p>
 *
 * @author jccarrillo@acm.org (JC Carrillo)
 * @see PersistJUnit4ClassRunner
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface PersistJUnit4Context {

	/**
	 * This value is used as the JPA unit name to start the
	 * {@link com.google.inject.persist.jpa.JpaPersistModule}.
	 *
	 * @return the JPA unit name to be loaded
	 */
	String jpaUnit() default "";

	/**
	 * This class will be instantiated and its properties will be used to start the
	 * {@link com.google.inject.persist.jpa.JpaPersistModule}.
	 *
	 * @return the {@link JpaProperties} containing JPA properties
	 */
	Class<? extends JpaProperties> jpaPropertiesClass() default DefaultJpaProperties.class;

	/**
	 * This array of {@link Module} classes will be installed by the {@link PersistJUnit4Module}.
	 *
	 * @return the modules to be installed
	 */
	Class<? extends Module>[] modules() default {};

	final class PersistJUnit4ContextHelper {

		/**
		 * Finds, validates and returns the {@link PersistJUnit4Context} from the test class.
		 *
		 * @return the {@link PersistJUnit4Context}.
		 */
		public static PersistJUnit4Context getPersistJUnit4Context(final Class<?> testClass) {
			final PersistJUnit4Context persistJUnit4Context = testClass.getAnnotation(PersistJUnit4Context.class);
			checkState(null != persistJUnit4Context, format("Missing @PersistContext annotation for test '%s'",
					testClass.getName()));
			checkState(null != persistJUnit4Context.jpaUnit(), format("Missing 'jpaUnit' on @PersistContext " +
					"annotation for test '%s'", testClass.getName()));

			return persistJUnit4Context;
		}
	}

	/**
	 * Provides a {@link Map} with JPA properties.
	 */
	interface JpaProperties {

		/**
		 * @return the JPA properties
		 */
		Map<?, ?> getProperties();
	}

	/**
	 * Default empty JPA properties.
	 */
	final class DefaultJpaProperties implements JpaProperties {

		@Override
		public Map<?, ?> getProperties() {
			return Collections.EMPTY_MAP;
		}
	}
}