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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.async.Async;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogReaderService;
import org.osgi.test.cases.async.junit.impl.AsyncErrorListener;
import org.osgi.test.cases.async.junit.impl.MyServiceAsyncDelegateFactory;
import org.osgi.test.cases.async.junit.impl.MyServiceException;
import org.osgi.test.cases.async.services.MyService;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.util.promise.Promise;
import org.osgi.util.tracker.ServiceTracker;

public class AsyncDelegateTestCase  {

	@InjectBundleContext
	BundleContext		context;
	private ServiceTracker<Async, Async>	asyncTracker;
	private Async	async;

	private ServiceTracker<LogReaderService, LogReaderService> logReaderTracker;
	private LogReaderService logReader;
	private AsyncErrorListener asyncErrors;
	private ExecutorService asyncExecutor;
	
	private MyServiceAsyncDelegateFactory myServiceDelegateFactory;

	private ServiceRegistration<MyService> delegateReg;
	
	@BeforeEach
	public void setUp() throws InterruptedException {
		asyncExecutor = Executors.newSingleThreadExecutor();
		myServiceDelegateFactory = new MyServiceAsyncDelegateFactory(asyncExecutor);
		delegateReg = context.registerService(MyService.class, myServiceDelegateFactory, null);

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
		delegateReg.unregister();
		asyncExecutor.shutdown();

		asyncTracker.close();
		logReader.removeLogListener(asyncErrors);
		logReaderTracker.close();
	}

	@Test
	public void testCallDelegateReturnPromise() throws Exception {
		MyService service = async.mediate(delegateReg.getReference(), MyService.class);

		Promise<Integer> p = async.call(service.countSlowly(2));
		assertThat(p.isDone()).isFalse();
		// Note that the delegate impl 2x the results so we can detect it was called
		assertThat(AsyncTestUtils.awaitResolve(p).intValue()).as("Wrong value.").isEqualTo(2 * 2);

		assertThat(myServiceDelegateFactory.getMySerivceImpl().getDelegateMethodCalled()).as("Wrong method called.").isEqualTo("async");
		assertThat(myServiceDelegateFactory.getMySerivceImpl().lastMethodCalled()).as("Wrong method called.").isEqualTo(MyService.METHOD_countSlowly);
		myServiceDelegateFactory.awaitUngetService();
	}

	@Test
	public void testCallDelegateReturnPromiseFail() throws Exception {
		MyService service = async.mediate(delegateReg.getReference(), MyService.class);

		Promise<Integer> p = async.call(service.failSlowly(2));
		assertThat(p.isDone()).isFalse();
		assertThat(AsyncTestUtils.awaitFailure(p) instanceof MyServiceException).as("Wrong failture type.").isTrue();

		assertThat(myServiceDelegateFactory.getMySerivceImpl().getDelegateMethodCalled()).as("Wrong method called.").isEqualTo("async");
		assertThat(myServiceDelegateFactory.getMySerivceImpl().lastMethodCalled()).as("Wrong method called.").isEqualTo(MyService.METHOD_failSlowly);
		myServiceDelegateFactory.awaitUngetService();
	}

	@Test
	public void testCallDelegateReturnNull() throws Exception {
		MyService service = async.mediate(delegateReg.getReference(), MyService.class);

		Promise<Integer> p = async.call(service.nonDelegateCountSlowly(2));
		assertThat(p.isDone()).isFalse();
		assertThat(AsyncTestUtils.awaitResolve(p).intValue()).as("Wrong value.").isEqualTo(2);

		assertThat(myServiceDelegateFactory.getMySerivceImpl().getDelegateMethodCalled()).as("Wrong method called.").isEqualTo("async");
		assertThat(myServiceDelegateFactory.getMySerivceImpl().lastMethodCalled()).as("Wrong method called.").isEqualTo(MyService.METHOD_nonDelegateCountSlowly);
		myServiceDelegateFactory.awaitUngetService();
	}

	@Test
	public void testCallDelegateReturnNullFail() throws Exception {
		MyService service = async.mediate(delegateReg.getReference(), MyService.class);

		Promise<Integer> p = async.call(service.nonDelegateFailSlowly(2));
		assertThat(p.isDone()).isFalse();
		assertThat(AsyncTestUtils.awaitFailure(p) instanceof MyServiceException).as("Wrong failture type.").isTrue();

		assertThat(myServiceDelegateFactory.getMySerivceImpl().getDelegateMethodCalled()).as("Wrong method called.").isEqualTo("async");
		assertThat(myServiceDelegateFactory.getMySerivceImpl().lastMethodCalled()).as("Wrong method called.").isEqualTo(MyService.METHOD_nonDelegateFailSlowly);
		myServiceDelegateFactory.awaitUngetService();
	}

	@Test
	public void testCallDelegateException() throws Exception {
		MyService service = async.mediate(delegateReg.getReference(), MyService.class);

		Promise<Integer> p = async.call(service.delegateFail());
		// fails instantaneously
		// assertFalse(p.isDone());
		assertThat(MyServiceException.class).as("Wrong failture type").isEqualTo(AsyncTestUtils.awaitFailure(p).getClass());

		assertThat(myServiceDelegateFactory.getMySerivceImpl().getDelegateMethodCalled()).as("Wrong method called.").isEqualTo("async");
		assertThat(myServiceDelegateFactory.getMySerivceImpl().lastMethodCalled()).as("No method should have been called.").isNull();
		myServiceDelegateFactory.awaitUngetService();
	}

	@Test
	public void testExecuteDelegateReturnTrue() throws Exception {
		MyService service = async.mediate(delegateReg.getReference(), MyService.class);

		service.countSlowly(2);
		async.execute();

		myServiceDelegateFactory.awaitUngetService();
		assertThat(myServiceDelegateFactory.getMySerivceImpl().getDelegateMethodCalled()).as("Wrong method called.").isEqualTo("execute");
		assertThat(myServiceDelegateFactory.getMySerivceImpl().lastMethodCalled()).as("Wrong method called.").isEqualTo(MyService.METHOD_countSlowly);
	}

	@Test
	public void testExecuteDelegateReturnTrueFail() throws Exception {
		MyService service = async.mediate(delegateReg.getReference(), MyService.class);

		service.failSlowly(2);
		// cannot check for logging here since the work is done on a thread outside of Async impl
		async.execute();

		assertThat(myServiceDelegateFactory.getMySerivceImpl().getDelegateMethodCalled()).as("Wrong method called.").isEqualTo("execute");
		assertThat(myServiceDelegateFactory.getMySerivceImpl().lastMethodCalled()).as("Wrong method called.").isEqualTo(MyService.METHOD_failSlowly);
		myServiceDelegateFactory.awaitUngetService();
	}

	@Test
	public void testExecuteDelegateReturnFalse() throws Exception {
		async.mediate(delegateReg.getReference(), MyService.class).nonDelegateCountSlowly(2);

		async.execute();

		myServiceDelegateFactory.awaitUngetService();
		assertThat(myServiceDelegateFactory.getMySerivceImpl().getDelegateMethodCalled()).as("Wrong method called.").isEqualTo("execute");
		assertThat(myServiceDelegateFactory.getMySerivceImpl().lastMethodCalled()).as("Wrong method called.").isEqualTo(MyService.METHOD_nonDelegateCountSlowly);
	}

	@Test
	public void testExecuteDelegateReturnFalseFail() throws Exception {
		MyService service = async.mediate(delegateReg.getReference(), MyService.class);

		service.nonDelegateFailSlowly(2);
		// cannot check for logging here since the work is done on a thread outside of Async impl
		async.execute();

		assertThat(myServiceDelegateFactory.getMySerivceImpl().getDelegateMethodCalled()).as("Wrong method called.").isEqualTo("execute");
		assertThat(myServiceDelegateFactory.getMySerivceImpl().lastMethodCalled()).as("Wrong method called.").isEqualTo(MyService.METHOD_nonDelegateFailSlowly);
		myServiceDelegateFactory.awaitUngetService();
	}

	@Test
	public void testExecuteDelegateException() throws Exception {
		async.mediate(delegateReg.getReference(), MyService.class).delegateFail();

		Promise<LogEntry> asyncErrorPromise = asyncErrors.getAsyncError();
		async.execute();
		Throwable error = AsyncTestUtils.awaitResolve(asyncErrorPromise).getException();
		assertThat(error instanceof MyServiceException).as("Wrong error type: " + error).isTrue();

		myServiceDelegateFactory.awaitUngetService();
		assertThat(myServiceDelegateFactory.getMySerivceImpl().getDelegateMethodCalled()).as("Wrong method called.").isEqualTo("execute");
		assertThat(myServiceDelegateFactory.getMySerivceImpl().lastMethodCalled()).as("No method should have been called.").isNull();
	}
}
