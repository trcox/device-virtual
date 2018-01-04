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
package org.edgexfoundry.device.virtual.service.impl;

import javax.annotation.PostConstruct;
import javax.ws.rs.NotFoundException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ImportResource;
import org.springframework.scheduling.annotation.Async;

import org.edgexfoundry.controller.AddressableClient;
import org.edgexfoundry.controller.DeviceServiceClient;
import org.edgexfoundry.device.domain.configuration.BaseService;
import org.edgexfoundry.device.virtual.Application;
import org.edgexfoundry.domain.meta.Addressable;
import org.edgexfoundry.domain.meta.AdminState;
import org.edgexfoundry.domain.meta.DeviceService;
import org.edgexfoundry.domain.meta.OperatingState;
import org.edgexfoundry.domain.meta.Protocol;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;

@ImportResource("spring-config.xml")
@ConfigurationProperties(prefix = "service")
@EnableConfigurationProperties
public class BaseServiceImpl implements BaseService {
	
	private final EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(this.getClass());
	
	private String name;
	private String host;
	private int port;
	private String[] labels;
	private String callback = "/api/v1/callback";
	private Protocol protocol = Protocol.HTTP;
	private AdminState adminState = AdminState.UNLOCKED;
  private OperatingState operatingState = OperatingState.ENABLED;
  private int connectRetries = 12;
  private long connectInterval = 10000;

	// TODO: This should become a service domain object , not a device service domain object
	private DeviceService service;

	// TODO: This should become a BaseServiceImplClient
	@Autowired
	private DeviceServiceClient deviceServiceClient;

	@Autowired
	private AddressableClient addressableClient;
	
	// track initialization attempts
	private int initAttempts;

	// track initialization success
	private boolean initialized;

	// track registration success
	private boolean registered;

  private Addressable addressable;
	
	public BaseServiceImpl() {
		setInitAttempts(0);
		setInitialized(false);
	}

	public int getInitAttempts() {
		return initAttempts;
	}

	public void setInitAttempts(int initAttempts) {
		this.initAttempts = initAttempts;
	}

	public int getConnectRetries() {
		return connectRetries;
	}

	public void setConnectRetries(int connectRetries) {
		this.connectRetries = connectRetries;
	}

	public long getConnectInterval() {
		return connectInterval;
	}

	public void setConnectInterval(long connectInterval) {
		this.connectInterval = connectInterval;
	}

	public boolean isInitialized() {
		return initialized;
	}

	public void setInitialized(boolean initialized) {
		this.initialized = initialized;
	}

	public boolean isRegistered() {
		return registered;
	}

	public void setRegistered(boolean registered) {
		logger.info("Service registered with id: " + service.getId());
		this.registered = registered;
	}

	// The base implementation always succeeds, derived classes customize
	public boolean initialize(String deviceServiceId) {
		return true;
	}
	
	@PostConstruct
	private void postConstructInitialize() {
		logger.debug("post construction initialization");
		attemptToInitialize();
	}
	
	@Async
	public void attemptToInitialize() {
		
		// count the attempt
		setInitAttempts(getInitAttempts() + 1);
		logger.debug("initialization attempt " + getInitAttempts());

		// first - get the service information or register service with metadata
		if(getService() != null) {
			// if we were able to get the service data we're registered
			setRegistered(true);
			// second - invoke any custom initialization method 
			setInitialized(initialize(getServiceId()));
		}

		// if both are successful, then we're done
		if(isRegistered() && isInitialized()) {
			logger.info("initialization successful.");
		} else {
			// otherwise see if we need to keep going
			if((getConnectRetries() == 0) || (getInitAttempts() < getConnectRetries())) {
				logger.debug("initialization unsuccessful. sleeping " + getConnectInterval());
				try {
					Thread.sleep(getConnectInterval());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				// start up the next thread
				attemptToInitialize();

			} else {
				// here, we've failed and run out of retries, so just be done.
				logger.info("initialization unsuccessful after " + getInitAttempts() + " attempts.  Giving up.");
				Application.exit(-1);
			}
		} 
	}
	
	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String[] getLabels() {
		return labels;
	}

	public void setLabels(String[] labels) {
		this.labels = labels;
	}

	public String getCallback() {
		return callback;
	}

	public void setCallback(String callback) {
		this.callback = callback;
	}

	public String getName() {
		return name;
	}

	public void setName(String serviceName) {
		this.name = serviceName;
	}

	public DeviceService getService() {
		if(service == null) {
			try {
				service = deviceServiceClient.deviceServiceForName(name);
				setService(service);
				logger.info("service " + name + " has service id " + service.getId());
			} catch (NotFoundException n) {
				try {
					setService();
				} catch (NotFoundException e) {
					logger.info("failed to create service " + name + " in metadata: " + e.getMessage());
					service = null;
				}
			} catch (Exception e) {
				logger.error("unable to establish connection to metadata " + e.getCause() + " " + e.getMessage());
				e.printStackTrace();
				service = null;
			}
		}
		return service;
	}

	private void setService() {
		logger.info("creating service " + name + " in metadata");
		service = new DeviceService();
		
		// Check for an addressable
		Addressable addressable = null;
		try {
			addressable = addressableClient.addressableForName(name);
		} catch (NotFoundException e) {
			// ignore this and create a new addressable
		}
		if(addressable == null) {
			addressable = new Addressable(name, protocol, host, callback, port);
			addressable.setOrigin(System.currentTimeMillis());
			try {
				String id = addressableClient.add(addressable);
				addressable.setId(id);
			} catch (NotFoundException e) {
				logger.error("Could not add addressable to metadata: " + e.getMessage());
				service = null;
				return;
			}
		}
		
		// Setup the service
		service.setAddressable(addressable);
		service.setOrigin(System.currentTimeMillis());
		service.setAdminState(adminState);
		service.setOperatingState(operatingState);
		service.setLabels(labels);
		service.setName(name);
		try {
			String id = deviceServiceClient.add(service);
			service.setId(id);
		} catch (NotFoundException e) {
			logger.error("Could not add device service to metadata: " + e.getMessage());
			service = null;
		}
	}
	
	public void setService(DeviceService srv) {
		service = srv;
		setName(srv.getName());
		setLabels(srv.getLabels());
		setAddressable(srv.getAddressable());
	}
	
	public Addressable getAddressable() {
	  return addressable;
	}

	public void setAddressable(Addressable addressable) {
    this.addressable = addressable;
	  setHost(addressable.getAddress());
    setPort(addressable.getPort());
    setCallback(addressable.getPath());
  }

  public boolean isServiceLocked(){
		DeviceService srv = getService();
		if (srv == null) {
		  return true;
		}
		return srv.getAdminState().equals(AdminState.LOCKED);
	}

	public String getServiceId() {
		return service.getId();
	}

}
