package com.google.inject.persist.jpa;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.persist.UnitOfWork;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

@Singleton
class ReentrantUnitOfWork implements UnitOfWork, Provider<EntityManager> {

	private static final Logger logger = Logger.getLogger(ReentrantUnitOfWork.class.getName());

	private static final ThreadLocal<Integer> reentrancyDepth = ThreadLocal.withInitial(() -> 0);
	private static final ThreadLocal<EntityManager> boundEntityManager = new ThreadLocal<>();
	private final Provider<EntityManagerFactory> entityManagerFactory;

	@Inject
	ReentrantUnitOfWork(Provider<EntityManagerFactory> entityManagerFactory) {
		this.entityManagerFactory = entityManagerFactory;
	}

	@Override
	public EntityManager get() {
		return (EntityManager) Proxy.newProxyInstance(
			getClass().getClassLoader(),
			new Class[] { EntityManager.class },
			(ignored, method, params) ->
				delegateToThreadBoundEntityManager(method, params)); // even when requested by a singleton
	}

	@Override
	public void begin() {
		reentrancyDepth.set(reentrancyDepth.get() + 1);
	}

	@Override
	public void end() {
		if (!isUnitOfWorkInProgress()) {
			logger.warning("Trying to close a unit of work which was never started, "
				+ "make sure your code has matching begin() and end() calls");
		}
		reentrancyDepth.set(reentrancyDepth.get() - 1);
		// in case someone calls begin() and end() without ever having requested an EntityManager
		if (!isUnitOfWorkInProgress() && isEntityManagerBound()) {
			finishUnitOfWork();
		}
	}

	protected void finishUnitOfWork() {
		try {
			boundEntityManager.get().close();
		} finally {
			boundEntityManager.remove();
		}
	}

	protected EntityManager lazyGet() {
		if (!isEntityManagerBound()) {
			boundEntityManager.set(entityManagerFactory.get().createEntityManager());
		}
		return boundEntityManager.get();
	}

	private boolean isUnitOfWorkInProgress() {
		return reentrancyDepth.get() > 0;
	}

	private boolean isEntityManagerBound() {
		return boundEntityManager.get() != null;
	}

	private Object delegateToThreadBoundEntityManager(Method method, Object[] params)
		throws IllegalAccessException, InvocationTargetException {
		Preconditions.checkState(
			isUnitOfWorkInProgress(),
			"Requested EntityManager outside work unit. "
				+ "Try calling UnitOfWork.begin() first, or use a PersistFilter if you "
				+ "are inside a servlet environment.");
		return method.invoke(lazyGet(), params);
	}
}
