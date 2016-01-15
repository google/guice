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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.UnitOfWork;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;

/**
 * Starts and stops persistence.
 *
 * @author jccarrillo@acm.org (JC Carrillo)
 */
@Singleton
public class PersistJUnit4RunListener extends RunListener {

	@Inject
	private PersistService persistService;

	@Inject
	private UnitOfWork unitOfWork;

	private boolean started = false;

	/**
	 * Starts the PersistService on the first test that runs.
	 *
	 * @param description
	 * @throws Exception
	 */
	@Override
	public synchronized void testStarted(Description description) throws Exception {
		if (started) {
			return; // already started persistence
		}

		this.persistService.start();
		this.unitOfWork.begin();
		this.started = true;
	}

	/**
	 * Stops persistence when the entire test finishes.
	 *
	 * @param result
	 * @throws Exception
	 */
	@Override
	public synchronized void testRunFinished(Result result) throws Exception {
		if (!started) {
			return; // never started
		}
		this.unitOfWork.end();
		this.persistService.stop();
	}
}