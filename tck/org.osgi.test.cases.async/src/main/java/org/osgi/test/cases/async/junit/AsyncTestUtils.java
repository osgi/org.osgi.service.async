/*******************************************************************************
 * Copyright (c) Contributors to the Eclipse Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0 
 *******************************************************************************/
package org.osgi.test.cases.async.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.osgi.framework.ServiceException;
import org.osgi.util.promise.Promise;


public class AsyncTestUtils {

	public static void checkForAsyncFailure(Promise<?> promise) throws InterruptedException {
		checkForAsyncFailure(promise.getFailure());
	}

	public static void checkForAsyncFailure(Throwable failure) {
		assertThat(failure).as("Expected failure from promise.").isNotNull();
		assertThat(failure instanceof ServiceException).as("Wrong failure type: " + failure.getClass().getName()).isTrue();
		assertThat(((ServiceException) failure).getType()).as("Expected ASYNC_ERROR type").isEqualTo(ServiceException.ASYNC_ERROR);
	}

	public static <T> T awaitResolve(Promise<T> promise) throws InterruptedException, InvocationTargetException {
		final CountDownLatch resolved = new CountDownLatch(1);
		promise.onResolve(new Runnable() {
			public void run() {
				resolved.countDown();
			}
		});
		resolved.await(5, TimeUnit.SECONDS);
		if (promise.isDone()) {
			return promise.getValue();
		}
		fail("No result in time.");
		return null;
	}

	public static Throwable awaitFailure(Promise<?> promise) throws InterruptedException, InvocationTargetException {
		final CountDownLatch resolved = new CountDownLatch(1);
		promise.onResolve(new Runnable() {
			public void run() {
				resolved.countDown();
			}
		});
		resolved.await(5, TimeUnit.SECONDS);
		if (promise.isDone()) {
			return promise.getFailure();
		}
		fail("No result in time.");
		return null;
	}

}
