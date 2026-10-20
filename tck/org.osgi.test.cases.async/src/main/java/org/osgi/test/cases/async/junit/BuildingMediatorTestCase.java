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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.async.Async;
import org.osgi.test.cases.async.junit.impl.MyServiceImpl.ExtendedFinalMethodMyServiceImpl;
import org.osgi.test.cases.async.junit.impl.MyServiceImpl.FinalMethodMyServiceImpl;
import org.osgi.test.cases.async.junit.impl.MyServiceImpl.FinalMyServiceImpl;
import org.osgi.test.cases.async.junit.impl.MyServiceImpl.NoZeroArgConstructorMyServiceImpl;
import org.osgi.test.cases.async.junit.impl.MyServiceImpl;
import org.osgi.test.cases.async.services.AnotherService;
import org.osgi.test.cases.async.services.MyService;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectBundleInstaller;
import org.osgi.test.common.install.BundleInstaller;
import org.osgi.util.tracker.ServiceTracker;

public class BuildingMediatorTestCase {

	@InjectBundleContext
	BundleContext		context;

	@InjectBundleInstaller
	BundleInstaller		installer;
	
	private ServiceTracker<Async, Async>	asyncTracker;
	private Async	async;

	private MyServiceImpl myService;
	private ServiceRegistration<MyService> normalReg;
	private ServiceRegistration<?> multiObjectClassReg;
	private ServiceRegistration<MyServiceImpl> implOnlyObjectClassReg;
	private ServiceRegistration<MyServiceImpl.FinalMyServiceImpl> finalClassReg;
	private ServiceRegistration<MyServiceImpl.FinalMethodMyServiceImpl> finalMethodReg;
	private ServiceRegistration<MyServiceImpl.ExtendedFinalMethodMyServiceImpl> extendFinalMethodReg;
	private ServiceRegistration<MyServiceImpl.NoZeroArgConstructorMyServiceImpl> noZeroArgConstructorReg;
	@SuppressWarnings("rawtypes")
	private ServiceRegistration<List> listReg;

	private Bundle clientBundle;
	
	@BeforeEach
	public void setUp() throws InterruptedException, BundleException, IOException {
		myService = new MyServiceImpl();

		normalReg = context.registerService(MyService.class, myService, null);
		multiObjectClassReg = context.registerService(new String[] {MyService.class.getName(), AnotherService.class.getName()}, myService, null);
		implOnlyObjectClassReg = context.registerService(MyServiceImpl.class, myService, null);
		finalClassReg = context.registerService(MyServiceImpl.FinalMyServiceImpl.class, new MyServiceImpl.FinalMyServiceImpl(), null);
		finalMethodReg = context.registerService(MyServiceImpl.FinalMethodMyServiceImpl.class, new MyServiceImpl.FinalMethodMyServiceImpl(), null);
		extendFinalMethodReg = context.registerService(MyServiceImpl.ExtendedFinalMethodMyServiceImpl.class, new MyServiceImpl.ExtendedFinalMethodMyServiceImpl(), null);
		noZeroArgConstructorReg = context.registerService(MyServiceImpl.NoZeroArgConstructorMyServiceImpl.class, new MyServiceImpl.NoZeroArgConstructorMyServiceImpl(1), null);
		
		listReg =  context.registerService(List.class, new ArrayList<String>(), null);

		asyncTracker = new ServiceTracker<Async, Async>(context, Async.class, null);
		asyncTracker.open();
		async = asyncTracker.waitForService(5000);

		assertThat(async).as("No Async service available within 5 seconds").isNotNull();

		clientBundle = installer.installBundle("tb1.jar", false);
		clientBundle.start();
	}
	
	@AfterEach
	public void tearDown() {
		normalReg.unregister();
		multiObjectClassReg.unregister();
		implOnlyObjectClassReg.unregister();
		finalClassReg.unregister();
		finalMethodReg.unregister();
		extendFinalMethodReg.unregister();
		noZeroArgConstructorReg.unregister();
		listReg.unregister();

		asyncTracker.close();

		try {
			clientBundle.stop();
		} catch (BundleException e) {
			// did out best
		}
		// Note that we don't uninstall clientBundle each time
	}

	private Async getClientAsync() {
		BundleContext context = clientBundle.getBundleContext();
		ServiceReference<Async> asyncRef = context.getServiceReference(Async.class);
		assertThat(asyncRef).as("No Async service for client.").isNotNull();
		Async clientAsync = context.getService(asyncRef);
		assertThat(clientAsync).as("Client Async is null.").isNotNull();
		return clientAsync;
	}

	private void assertIsAssignable(Object service, Class<?>... classes) {
		for (Class<?> clazz : classes) {
			assertThat(clazz.isAssignableFrom(service.getClass())).as("The service is not assignable to: " + clazz.getName()).isTrue();
		}
	}

	private void assertNotAssignable(Object service, Class<?>... classes) {
		for (Class<?> clazz : classes) {
			assertThat(clazz.isAssignableFrom(service.getClass())).as("The service is assignable to: " + clazz.getName()).isFalse();
		}
	}

	@Test
	public void testReferenceToSingleObjectClassOnly() throws Exception {
		Object service = async.mediate(normalReg.getReference(), MyService.class);
		assertIsAssignable(service, MyService.class);
		assertNotAssignable(service, AnotherService.class, MyServiceImpl.class);
	}

	/* Added by tim.ward@paremus.com to cover new mediator construction rules */
	
	@SuppressWarnings("unchecked")
	@Test
	public void testReferenceToSingleObjectClassMediatedToWrongType() throws Exception {
		@SuppressWarnings("rawtypes")
		Object service = async.mediate((ServiceReference) normalReg.getReference(), AnotherService.class);
		assertIsAssignable(service, AnotherService.class);
		assertNotAssignable(service, MyService.class, MyServiceImpl.class);
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testReferenceToMultiObjectClassOnly() throws Exception {
		Object service = async.mediate((ServiceReference<MyService>)multiObjectClassReg.getReference(), MyService.class);
		assertIsAssignable(service, MyService.class);
		assertNotAssignable(service, AnotherService.class, MyServiceImpl.class);
		service = async.mediate((ServiceReference<AnotherService>)multiObjectClassReg.getReference(), AnotherService.class);
		assertIsAssignable(service, AnotherService.class);
		assertNotAssignable(service, MyService.class, MyServiceImpl.class);
	}
	/* End additions */

	@Test
	public void testReferenceToImplClass() throws Exception {
		Object service = async.mediate(implOnlyObjectClassReg.getReference(), MyServiceImpl.class);
		assertIsAssignable(service, MyService.class, AnotherService.class, MyServiceImpl.class);
	}

	@Test
	public void testDirectToImplClass() throws Exception {
		Object service = async.mediate(myService, MyServiceImpl.class);
		assertIsAssignable(service, MyService.class, AnotherService.class, MyServiceImpl.class);
		
		/* Added by tim.ward@paremus.com to cover new mediator construction rules */
		
		service = async.mediate(myService, AnotherService.class);
		assertIsAssignable(service, AnotherService.class);
		assertNotAssignable(service, MyService.class, MyServiceImpl.class);

		service = async.mediate(myService, MyService.class);
		assertIsAssignable(service, MyService.class);
		assertNotAssignable(service, AnotherService.class, MyServiceImpl.class);
		
		/* End additions */
	}

	@Test
	public void testReferenceMediateToFinalClass() {
		try {
			async.mediate(finalClassReg.getReference(), FinalMyServiceImpl.class);
			fail("Should not be able to mediate a final class");
		} catch (IllegalArgumentException iee) {
			//Expected
		}
	}

	@Test
	public void testDirectMediateToFinalClass() {
		try {
			async.mediate(new MyServiceImpl.FinalMyServiceImpl(), FinalMyServiceImpl.class);
			fail("Should not be able to mediate a final class");
		} catch (IllegalArgumentException iee) {
			//Expected
		}
	}

	@Test
	public void testReferenceMediateToFinalMethod() {
		try {
			async.mediate(finalMethodReg.getReference(), FinalMethodMyServiceImpl.class);
			fail("Should not be able to mediate a class with a final method");
		} catch (IllegalArgumentException iee) {
			//Expected
		}
	}

	@Test
	public void testDirectMediateToFinalMethod() {
		try {
			async.mediate(new MyServiceImpl.FinalMethodMyServiceImpl(), FinalMethodMyServiceImpl.class);
			fail("Should not be able to mediate a class with a final method");
		} catch (IllegalArgumentException iee) {
			//Expected
		}
	}

	@Test
	public void testReferenceMediateToExtendFinalClass() {
		try {
			async.mediate(extendFinalMethodReg.getReference(), ExtendedFinalMethodMyServiceImpl.class);
			fail("Should not be able to mediate a class with a final method");
		} catch (IllegalArgumentException iee) {
			//Expected
		}
	}

	@Test
	public void testDirectMediateToExtendFinalMethod() {
		try {
			async.mediate(new MyServiceImpl.ExtendedFinalMethodMyServiceImpl(), 
				ExtendedFinalMethodMyServiceImpl.class);
			fail("Should not be able to mediate a class with a final method");
		} catch (IllegalArgumentException iee) {
			//Expected
		}
	}

	@Test
	public void testReferenceMediateToNoZeroArgConstructor() {
		try {
			async.mediate(noZeroArgConstructorReg.getReference(), NoZeroArgConstructorMyServiceImpl.class);
			fail("Should not be able to mediate a class no zero args constructor");
		} catch (IllegalArgumentException iee) {
			//Expected
		}
	}

	@Test
	public void testDirectMediateToNoZeroArgConstructor() {
		try {
			async.mediate(new MyServiceImpl.NoZeroArgConstructorMyServiceImpl(1), NoZeroArgConstructorMyServiceImpl.class);
			fail("Should not be able to mediate a class no zero args constructor");
		} catch (IllegalArgumentException iee) {
			//Expected
		}
	}

	@Test
	public void testReferenceMediateToTypesNotVisibleToClient() {
		Object service = getClientAsync().mediate(normalReg.getReference(), MyService.class);
		assertIsAssignable(service, MyService.class);
		assertNotAssignable(service, AnotherService.class, MyServiceImpl.class);
	}

	@Test
	public void testDirectMediateToTypesNotVisibleToClient() {
		Object service = getClientAsync().mediate(myService, MyServiceImpl.class);
		assertIsAssignable(service, MyService.class, AnotherService.class, MyServiceImpl.class);
	}

	@Test
	public void testReferenceMediateToVMTypes() {
		Object service = async.mediate(listReg.getReference(), List.class);
		assertIsAssignable(service, List.class);
	}

	@Test
	public void testDirectMediateToVMTypes() {
		Object service = async.mediate(new ArrayList<String>(), ArrayList.class);
		assertIsAssignable(service, List.class, ArrayList.class);
	}
}
