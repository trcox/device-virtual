/*******************************************************************************
 * Copyright 2016-2017 Dell Inc.
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
 *
 * @microservice:  device-sdk-tools
 * @author: Tyler Cox, Dell
 * @version: 1.0.0
 *******************************************************************************/
package org.edgexfoundry.device.virtual;

import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.edgexfoundry.device.virtual.config.ApplicationProperties;
import org.edgexfoundry.device.virtual.handler.VirtualHandler;
import org.edgexfoundry.device.scheduling.Scheduler;
import org.edgexfoundry.device.store.DeviceStore;
import org.edgexfoundry.device.virtual.service.CleanupService;
import org.edgexfoundry.device.virtual.service.DeviceAutoCreateService;
import org.edgexfoundry.device.virtual.service.ProvisionService;
import org.edgexfoundry.device.virtual.service.VirtualResourceManager;
import org.edgexfoundry.device.virtual.service.impl.BaseServiceImpl;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;

@Service
public class Initializer extends BaseServiceImpl {

	private final EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(this.getClass());
	
	@Autowired
	DeviceStore devices;
	
	@Autowired
	Scheduler schedules;
	
	@Autowired
	VirtualHandler virtualHandler;
	
	@Autowired
	private ApplicationProperties applicationProperties;
	
	@Autowired
	private CleanupService cleanupService;
	
	@Autowired
  private ProvisionService deviceProvisionService;

  @Autowired
  private DeviceAutoCreateService deviceAutoCreateService;

  @Autowired
  private VirtualResourceManager virtualResourceManager;

	@Override
	public boolean initialize(String deviceServiceId) {
	  deviceProvisionService.doProvision();
	  
		// load the devices in cache.
		devices.initialize(deviceServiceId, virtualHandler);
		schedules.initialize(getName());

    if (applicationProperties.isAutoCreateDevice()) {
      deviceAutoCreateService.autoCreateOneDeviceForEachProfile();
    }
    
    virtualResourceManager.createDefaultRecordsForExistingDevices();
    
    logger.info("Initialized device service successfully");
		return true;
	}
	
	@PreDestroy
	public void cleanup() {
		if (applicationProperties.isAutoCleanup()) {
			cleanupService.doCleanup();
		}
	}
	
}
