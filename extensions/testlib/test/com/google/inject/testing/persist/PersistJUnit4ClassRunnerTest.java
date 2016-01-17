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
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.Transactional;
import com.google.inject.testing.persist.jpa.JpaTestEntity;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

/**
 * @author jccarrillo@acm.org (JC Carrillo)
 */
@RunWith(PersistJUnit4ClassRunner.class)
@PersistJUnit4Context(jpaUnit = "testUnit", modules = {PersistJUnit4ClassRunnerTest.SampleModule.class},
		jpaPropertiesClass = PersistJUnit4ClassRunnerTest.SampleJpaProperties.class)
public class PersistJUnit4ClassRunnerTest {

	private static final String UNIQUE_TEXT = "PersistContext:  some unique text" + new Date();
	private static final String TRANSIENT_UNIQUE_TEXT = "PersistContext: some other unique text" + new Date();

	@Inject
	private Injector injector;

	@Test
	public void testEntityManagerExists() {
		EntityManager session = this.injector.getInstance(EntityManager.class);
		Assert.assertNotNull("EntityManager was not injected", session);
		EntityTransaction transaction = session.getTransaction();
		Assert.assertFalse("EntityManager was active", transaction.isActive());
	}

	@Test(expected = IllegalStateException.class)
	public void testPersistServiceStarted() {
		PersistService persistService = this.injector.getInstance(PersistService.class);
		persistService.start();
		EntityManager session = this.injector.getInstance(EntityManager.class);
		Assert.assertFalse("EntityManager was not closed by transactional service",
				session.getTransaction().isActive());
	}

	@Test
	public void testSimpleTransaction() {
		this.injector.getInstance(JpaTestEntityDao.class).runOperationInTxn();

		EntityManager em = this.injector.getInstance(EntityManager.class);
		Assert.assertFalse("txn was not closed by transactional service", em.getTransaction().isActive());

		//test that the data has been stored
		Object result = em.createQuery("from JpaTestEntity where text = :text")
				.setParameter("text", UNIQUE_TEXT).getSingleResult();

		Assert.assertTrue("odd result returned fatal", result instanceof JpaTestEntity);

		Assert.assertEquals("queried entity did not match--did automatic txn fail?",
				UNIQUE_TEXT, ((JpaTestEntity) result).getText());
	}

	@Test(expected = NoResultException.class)
	public void testSimpleNonTransaction() {
		EntityManager em = this.injector.getInstance(EntityManager.class);

		Assert.assertFalse("txn was active", em.getTransaction().isActive());
		this.injector.getInstance(JpaTestEntityDao.class).runOperationInNonTxn();

		Assert.assertFalse("txn was not closed by transactional service", em.getTransaction().isActive());

		Object result = em.createQuery("from JpaTestEntity where text = :text")
				.setParameter("text", TRANSIENT_UNIQUE_TEXT).getSingleResult();
	}

	public interface SampleInterface {

	}

	public static class JpaTestEntityDao {

		@Inject
		private EntityManager em;

		@Transactional
		public void runOperationInTxn() {
			JpaTestEntity entity = new JpaTestEntity();
			entity.setText(UNIQUE_TEXT);
			em.persist(entity);
		}

		public void runOperationInNonTxn() {
			JpaTestEntity entity = new JpaTestEntity();
			entity.setText(TRANSIENT_UNIQUE_TEXT);
			em.persist(entity);
		}
	}

	public static class SampleModule extends AbstractModule {

		private static class SampleInterfaceImpl implements SampleInterface {
		}

		@Override
		protected final void configure() {
			bind(SampleInterface.class).to(SampleInterfaceImpl.class);
		}
	}

	public static class SampleJpaProperties implements PersistJUnit4Context.JpaProperties {

		@Override
		public Map<?, ?> getProperties() {
			return Collections.EMPTY_MAP;
		}
	}
}