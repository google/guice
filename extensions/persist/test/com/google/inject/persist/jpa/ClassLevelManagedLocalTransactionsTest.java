package com.google.inject.persist.jpa;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.FileNotFoundException;
import java.io.IOException;
import javax.persistence.EntityManager;

public class ClassLevelManagedLocalTransactionsTest
	extends BaseManagedLocalTransactionsTest<ClassLevelManagedLocalTransactionsTest.ClassLevelTransactionalObjectImpl> {

	@Transactional(rollbackOn = IOException.class, ignore = FileNotFoundException.class)
	public static class ClassLevelTransactionalObjectImpl extends TransactionalObjectImpl {

		@Inject
		EntityManager session;

		@Override
		protected EntityManager getSession() {
			return session;
		}

		@Override
		public void runOperationInTxn() {
			super.runOperationInTxn();
		}

		@Override
		public void runOperationInTxnThrowingChecked() throws IOException {
			super.runOperationInTxnThrowingChecked();
		}

		@Transactional // needed to override class-level settings
		@Override
		public void runOperationInTxnThrowingUnchecked() {
			super.runOperationInTxnThrowingUnchecked();
		}

		@Override
		public void runOperationInTxnThrowingCheckedException() throws IOException {
			super.runOperationInTxnThrowingCheckedException();
		}
	}

	@Override
	protected Class<ClassLevelTransactionalObjectImpl> getTransactionalObjectType() {
		return ClassLevelTransactionalObjectImpl.class;
	}
}
