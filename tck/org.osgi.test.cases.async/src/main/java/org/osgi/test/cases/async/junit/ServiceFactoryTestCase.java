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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.async.Async;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogReaderService;
import org.osgi.test.cases.async.junit.impl.AsyncErrorListener;
import org.osgi.test.cases.async.junit.impl.MyServiceException;
import org.osgi.test.cases.async.junit.impl.MyServiceFactory;
import org.osgi.test.cases.async.services.MyService;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.util.promise.Promise;
import org.osgi.util.tracker.ServiceTracker;

public class ServiceFactoryTestCase {

	@InjectBundleContext
	BundleContext		context;
	
	private ServiceTracker<Async, Async>	asyncTracker;
	private Async	async;

	private ServiceTracker<LogReaderService, LogReaderService> logReaderTracker;
	private LogReaderService logReader;
	private AsyncErrorListener asyncErrors;
	
	private MyServiceFactory myServiceFactory;
	private ServiceRegistration<MyService> factoryReg;
	
	@BeforeEach
	public void setUp() throws InterruptedException {
		myServiceFactory = new MyServiceFactory();
		factoryReg = context.registerService(MyService.class, myServiceFactory, null);

		asyncTracker = new ServiceTracker<Async, Async>(context, Async.class, null);
		asyncTracker.open();
		async = asyncTracker.waitForService(5000);

		assertThat(async).as("No Async service available within 5 seconds").isNotNull();

		logReaderTracker = new ServiceTracker<LogReaderService, LogReaderService>(context, LogReaderService.class, null);
		logReaderTracker.open();
		logReader = logReaderTracker.waitForService(5000);
		assertThat(logReader).as("No LogReaderService available within 5 seconds").isNotNull();
		logReader.addLogListener(asyncErrors  = new AsyncErrorListener());
	}
	
	@AfterEach
	public void tearDown() {
		factoryReg.unregister();

		asyncTracker.close();
		logReader.removeLogListener(asyncErrors);
		logReaderTracker.close();
	}
	

	@Test
	public void testAsyncCallGetUngetServiceCalls() throws Exception {
		MyService service = async.mediate(factoryReg.getReference(), MyService.class);

		Promise<Integer> p = async.call(service.countSlowly(2));
		assertThat(p.isDone()).isFalse();
		assertThat(AsyncTestUtils.awaitResolve(p).intValue()).as("Wrong value.").isEqualTo(2);

		assertThat(myServiceFactory.getMySerivceImpl().lastMethodCalled()).as("Wrong method called.").isEqualTo(MyService.METHOD_countSlowly);
		myServiceFactory.awaitUngetService();
	}

	@Test
	public void testAsyncExecuteGetUngetServiceCalls() throws Exception {
		async.mediate(factoryReg.getReference(), MyService.class).countSlowly(2);

		async.execute();

		myServiceFactory.awaitUngetService();
		assertThat(myServiceFactory.getMySerivceImpl().lastMethodCalled()).as("Wrong method called.").isEqualTo(MyService.METHOD_countSlowly);
	}

	@Test
	public void testAsyncCallGetUngetServiceCallsWithFailure() throws Exception {
		MyService service = async.mediate(factoryReg.getReference(), MyService.class);

		Promise<Integer> p = async.call(service.failSlowly(2));
		assertThat(p.isDone()).isFalse();
		assertThat(AsyncTestUtils.awaitFailure(p) instanceof MyServiceException).as("Wrong failture type.").isTrue();

		assertThat(myServiceFactory.getMySerivceImpl().lastMethodCalled()).as("Wrong method called.").isEqualTo(MyService.METHOD_failSlowly);
		myServiceFactory.awaitUngetService();
	}

	@Test
	public void testAsyncExecuteGetUngetServiceCallsWithFailure() throws Exception {
		async.mediate(factoryReg.getReference(), MyService.class).failSlowly(2);

		Promise<LogEntry> asyncErrorPromise = asyncErrors.getAsyncError();
		async.execute();
		Throwable error = AsyncTestUtils.awaitResolve(asyncErrorPromise).getException();
		assertThat(error instanceof MyServiceException).as("Wrong error type: " + error).isTrue();

		myServiceFactory.awaitUngetService();
		assertThat(myServiceFactory.getMySerivceImpl().lastMethodCalled()).as("Wrong method called.").isEqualTo(MyService.METHOD_failSlowly);
	}
}
