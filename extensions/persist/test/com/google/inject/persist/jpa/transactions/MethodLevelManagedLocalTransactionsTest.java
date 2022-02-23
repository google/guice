/*
 * Copyright (C) 2010 Google, Inc.
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

package com.google.inject.persist.jpa.transactions;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.finder.Finder;
import com.google.inject.persist.jpa.entities.JpaTestEntity;
import java.io.FileNotFoundException;
import java.io.IOException;
import javax.persistence.EntityManager;

/**
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */

public class MethodLevelManagedLocalTransactionsTest
    extends
    BaseManagedLocalTransactionsTest<MethodLevelManagedLocalTransactionsTest.MethodLevelTransactionalObjectImpl> {

  @Override
  protected Class<MethodLevelTransactionalObjectImpl> getTransactionalObjectType() {
    return MethodLevelTransactionalObjectImpl.class;
  }

  public static class MethodLevelTransactionalObjectImpl extends TransactionalObjectImpl {
    private final EntityManager session;

    @Inject
    public MethodLevelTransactionalObjectImpl(EntityManager em) {
      this.session = em;
    }

    @Override
    protected EntityManager getSession() {
      return session;
    }

    @Transactional
    @Override
    public void runOperationInTxn() {
      super.runOperationInTxn();
    }

    @Transactional(rollbackOn = IOException.class)
    @Override
    public void runOperationInTxnThrowingChecked() throws IOException {
      super.runOperationInTxnThrowingChecked();
    }

    @Transactional
    @Override
    public void runOperationInTxnThrowingUnchecked() {
      super.runOperationInTxnThrowingUnchecked();
    }

    @Transactional(rollbackOn = IOException.class, ignore = FileNotFoundException.class)
    @Override
    public void runOperationInTxnThrowingCheckedException() throws IOException {
      super.runOperationInTxnThrowingCheckedException();
    }

    @Finder(query = "SELECT e FROM JpaTestEntity e WHERE e.text = :text")
    public JpaTestEntity find(@Named("text") String text) {
      return null;
    }
  }
}
