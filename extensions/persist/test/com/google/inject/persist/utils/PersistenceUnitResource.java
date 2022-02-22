package com.google.inject.persist.utils;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class PersistenceUnitResource extends SuiteAndTestResource {
	private final String unitName;

	public PersistenceUnitResource(String unitName) {
		super(Lifecycle.SUITE);
		this.unitName = unitName;
	}

	private EntityManagerFactory emf;
	private EntityManager session;

	public EntityManagerFactory getEmf() {
		return emf;
	}

	public EntityManager getSession() {
		return session;
	}

	@Override
	protected void beforeTest() {
		session = emf.createEntityManager();
	}

	@Override
	protected void beforeSuite() {
		emf = Persistence.createEntityManagerFactory(unitName);
	}

	@Override
	protected void afterTest() {
		session.close();
	}

	@Override
	protected void afterSuite() {
		emf.close();
	}
}
