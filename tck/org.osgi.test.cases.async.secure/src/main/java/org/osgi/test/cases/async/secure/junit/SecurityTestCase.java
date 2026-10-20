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
package org.osgi.test.cases.async.secure.junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.async.Async;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogReaderService;
import org.osgi.test.cases.async.secure.junit.impl.AsyncErrorListener;
import org.osgi.test.cases.async.secure.junit.impl.MyServiceImpl;
import org.osgi.test.cases.async.secure.services.MyService;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectBundleInstaller;
import org.osgi.test.common.install.BundleInstaller;
import org.osgi.util.promise.Promise;
import org.osgi.util.tracker.ServiceTracker;

public class SecurityTestCase {

	@InjectBundleContext
	BundleContext		context;

	@InjectBundleInstaller
	BundleInstaller		installer;

	String				testMethodName;

	@BeforeEach
	void testMethodName(TestInfo testInfo) {
		testMethodName = testInfo.getTestMethod().get().getName();
	}

	private ServiceTracker<Async, Async>	asyncTracker;
	private Async	async;

	private ServiceTracker<LogReaderService, LogReaderService> logReaderTracker;
	private LogReaderService logReader;
	private AsyncErrorListener asyncErrors;

	private MyServiceImpl myService;
	private ServiceRegistration<MyService> normalReg;

	private Bundle tb1Bundle;
	private Bundle tb2Bundle;
	
	@BeforeEach
	public void setUp() throws InterruptedException, BundleException, IOException {
		myService = new MyServiceImpl();

		normalReg = context.registerService(MyService.class, myService, null);

		asyncTracker = new ServiceTracker<Async, Async>(context, Async.class, null);
		asyncTracker.open();
		async = asyncTracker.waitForService(5000);

		assertThat(async).as("No Async service available within 5 seconds").isNotNull();

		logReaderTracker = new ServiceTracker<LogReaderService, LogReaderService>(context, LogReaderService.class, null);
		logReaderTracker.open();
		logReader = logReaderTracker.waitForService(5000);
		assertThat(logReader).as("No LogReaderService available within 5 seconds").isNotNull();
		logReader.addLogListener(asyncErrors  = new AsyncErrorListener());

		tb1Bundle = installer.installBundle("tb1.jar", false);
		tb1Bundle.start();
		tb2Bundle = installer.installBundle("tb2.jar", false);
		tb2Bundle.start();
	}
	
	@AfterEach
	public void tearDown() {
		normalReg.unregister();

		asyncTracker.close();

		logReader.removeLogListener(asyncErrors);
		logReaderTracker.close();

		try {
			tb1Bundle.stop();
		} catch (BundleException e) {
			// did out best
		}
		try {
			tb2Bundle.stop();
		} catch (BundleException e) {
			// did out best
		}
		// Note that we don't uninstall tb bundles each time
	}

	@Test
	public void testmediateReferenceWithSecurityCheck_tb1() throws Exception {
		assertMediateSuccess();
	}

	@Test
	public void testmediateReferenceWithSecurityCheck_tb2() throws Exception {
		assertMediateSuccess();
	}

	@Test
	public void testmediateDirectWithSecurityCheck_tb1() throws Exception {
		assertMediateSuccess();
	}

	@Test
	public void testmediateDirectWithSecurityCheck_tb2() throws Exception {
		assertMediateSuccess();
	}

	@Test
	public void testcallWithSecurityCheck_tb1() throws Exception {
		assertCallFailure();
	}

	@Test
	public void testcallWithoutSecurityCheck_tb1() throws Exception {
		assertCallSuccess();
	}

	@Test
	public void testcallWithSecurityCheck_tb2() throws Exception {
		assertCallSuccess();
	}

	@Test
	public void testcallWithoutSecurityCheck_tb2() throws Exception {
		assertCallSuccess();
	}

	@Test
	public void testexecuteWithSecurityCheck_tb1() throws Exception {
		assertExecuteFailure();
	}

	@Test
	public void testexecuteWithoutSecurityCheck_tb1() throws Exception {
		assertExecuteSuccess();
	}

	@Test
	public void testexecuteWithSecurityCheck_tb2() throws Exception {
		assertExecuteSuccess();
	}

	@Test
	public void testexecuteWithoutSecurityCheck_tb2() throws Exception {
		assertExecuteSuccess();
	}

	private void assertMediateSuccess() throws Exception {
		getCallable().call();
	}

	private void assertCallFailure() throws Exception {
		Callable<Promise<Integer>> callable = asPromiseInteger(getCallable());
		Promise<Integer> promise = callable.call();
		Throwable t = AsyncTestUtils.awaitFailure(promise);
		assertThat(t instanceof SecurityException).as("Wrong failure exception: " + t).isTrue();
		assertThat(myService.lastMethodCalled()).as("Wrong method called.").isEqualTo(MyService.METHOD_countSlowly);
	}

	private void assertCallSuccess() throws Exception {
		Callable<Promise<Integer>> callable = asPromiseInteger(getCallable());
		Promise<Integer> promise = callable.call();
		Integer result = AsyncTestUtils.awaitResolve(promise);
		assertThat(result).as("Wrong result.").isEqualTo(Integer.valueOf(2));
		assertThat(myService.lastMethodCalled()).as("Wrong method called.").isEqualTo(MyService.METHOD_countSlowly);
	}

	private void assertExecuteFailure() throws Exception {
		Callable<Void> callable = asVoid(getCallable());
		callable.call();
		Promise<LogEntry> securityError = asyncErrors.getSecurityError();
		Throwable t = AsyncTestUtils.awaitResolve(securityError).getException();
		assertThat(t instanceof SecurityException).as("Wrong failure exception: " + t).isTrue();
		assertThat(myService.lastMethodCalled()).as("Wrong method called.").isEqualTo(MyService.METHOD_countSlowly);
	}

	private void assertExecuteSuccess() throws Exception {
		Callable<Void> callable = asVoid(getCallable());
		callable.call();
		Integer result = myService.awaitSuccess();
		assertThat(result).as("Wrong result.").isEqualTo(Integer.valueOf(2));
		assertThat(myService.lastMethodCalled()).as("Wrong method called.").isEqualTo(MyService.METHOD_countSlowly);
	}

	@SuppressWarnings("unchecked")
	private Callable<Promise<Integer>> asPromiseInteger(Callable<?> callable) {
		return (Callable<Promise<Integer>>) callable;
	}

	@SuppressWarnings("unchecked")
	private Callable<Void> asVoid(Callable<?> callable) {
		return (Callable<Void>) callable;
	}

	@SuppressWarnings("rawtypes")
	private Callable getCallable() throws InvalidSyntaxException {
		String testName = testMethodName;
		testName = testName.substring("test".length());
		Collection<ServiceReference<Callable>> callable = context.getServiceReferences(Callable.class, "(" + MyService.TEST_KEY + "=" + testName + ")");
		assertThat(callable).as("No testCallable found: " + testName).isNotEmpty();
		return context.getService(callable.iterator().next());
	}

}
