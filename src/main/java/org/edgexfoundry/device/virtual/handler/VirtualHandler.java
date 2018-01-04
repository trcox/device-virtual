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
package org.edgexfoundry.device.virtual.handler;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.edgexfoundry.device.domain.ResponseObject;
import org.edgexfoundry.device.domain.ScanList;
import org.edgexfoundry.device.domain.ServiceObject;
import org.edgexfoundry.device.domain.Transaction;
import org.edgexfoundry.device.store.impl.ObjectStoreImpl;
import org.edgexfoundry.device.store.impl.ProfileStoreImpl;
import org.edgexfoundry.device.virtual.DeviceDiscovery;
import org.edgexfoundry.device.virtual.ObjectTransformImpl;
import org.edgexfoundry.device.virtual.VirtualDriver;
import org.edgexfoundry.device.virtual.domain.VirtualObject;
import org.edgexfoundry.domain.core.Reading;
import org.edgexfoundry.domain.meta.Device;
import org.edgexfoundry.domain.meta.PropertyValue;
import org.edgexfoundry.domain.meta.ResourceOperation;
import org.edgexfoundry.exception.controller.NotFoundException;
import org.edgexfoundry.exception.controller.ServiceException;
import org.edgexfoundry.service.handler.ServiceHandler;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Service
public class VirtualHandler implements ServiceHandler {

	private final EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(this.getClass());
	
	@Autowired
	private VirtualDriver driver;
	
	@Autowired
	private DeviceDiscovery discover;
	
	@Autowired
	private ProfileStoreImpl profiles;
	
	@Autowired
	private ObjectTransformImpl transform;
	
	@Autowired
	private ObjectStoreImpl objectCache;
	
	@Autowired
	private CoreDataMessageHandler processor;
	
	@Value("${virtual.device.init:#{null}}")
	private String virtualInit;
	@Value("${virtual.device.init.args:#{null}}")
	private String virtualInitArgs;
	
	@Value("${virtual.device.remove:#{null}}")
	private String virtualRemove;
	@Value("${virtual.device.remove.args:#{null}}")
	private String virtualRemoveArgs;
	
	public Map<String, Transaction> transactions = new HashMap<String, Transaction>();
	
	public void initialize() {
		if (driver != null)
			driver.initialize();
	}

	public void initializeDevice(Device device) {
		if(virtualInit != null && commandExists(device, virtualInit, "set"))
			executeCommand(device, virtualInit, virtualInitArgs, "set");
		logger.info("Initialized Device: " + device.getName());
	}

	public void disconnectDevice(Device device) {
		if (virtualRemove != null && commandExists(device, virtualRemove, "set"))
			executeCommand(device, virtualRemove, virtualRemoveArgs, "set");
		driver.disconnectDevice(device.getAddressable());
		logger.info("Disconnected Device: " + device.getName());
	}
	
	public void scan() {
		ScanList availableList = null;
		availableList = driver.discover();
		discover.provision(availableList);
	}
	
	@Override
	public boolean commandExists(Device device, String command, String operation) {
		List<ResourceOperation> cmdsForDevice = profiles.getCommandList(device.getName(), command, operation);
		if (cmdsForDevice == null || cmdsForDevice.isEmpty())
			return false;
		return true;
	}

	@Override
	public Map<String, String> executeCommand(Device device, String cmd, String arguments, String method) {
		// set immediate flag to false to read from object cache of last readings
		Boolean immediate = true;
		Transaction transaction = new Transaction();
		String transactionId = transaction.getTransactionId();
		transactions.put(transactionId, transaction);
		executeOperations(device, cmd, arguments, immediate, transactionId, method);
		
		synchronized (transactions) {
			while (!transactions.get(transactionId).isFinished()) {
				try {
					transactions.wait();
				} catch (InterruptedException e) {
					// Exit quietly on break
					return null;
				}
			}
		}
	
		List<Reading> readings = transactions.get(transactionId).getReadings();
		transactions.remove(transactionId);
		
		return sendTransaction(device.getName(), readings);
	}
	
	public Map<String, String> sendTransaction(String deviceName, List<Reading> readings) {
		Map<String, String> valueDescriptorMap = new HashMap<String,String>();
		List<ResponseObject> resps = processor.sendCoreData(deviceName, readings);
		for (ResponseObject obj: resps)
			valueDescriptorMap.put(obj.getName(), obj.getValue());
		return valueDescriptorMap;
	}

	private void executeOperations(Device device, String commandName, String arguments, Boolean immediate, String transactionId, String method) {		
		String deviceName = device.getName();

		// get the operations for this device's object operation method
		List<ResourceOperation> operations = getResourceOperations(deviceName, transactionId, commandName, method);
		List<ResourceOperation> getOperations = retrieveGetResourceOperations(deviceName, commandName);
				
		for (ResourceOperation operation: operations) {
			String opResource = operation.getResource();
			if (opResource != null) {
				executeOperations(device, opResource, arguments, immediate, transactionId, operation.getOperation());
				continue;
			}

			String objectName = operation.getObject();
			// get the object for this device operation
	    VirtualObject object = (VirtualObject) profiles.getServiceObject(deviceName, objectName);
			checkVirtualObject(object, objectName, transactionId);
			
			//TODO Add property flexibility
			if (!operation.getProperty().equals("value"))
				throw new ServiceException(new UnsupportedOperationException("Only property of value is implemented for this service!"));
			
			String val = null;
			
			if (method.equals("set"))
				val = parseArguments(arguments, operation, device, object, getOperations);
			
			// command operation for client processing
			if (requiresQuery(immediate, method, deviceName, operation)) {
				String opId = transactions.get(transactionId).newOpId();
				final String parameter = val;
				new Thread(() -> driver.process(operation, device, object, parameter, transactionId, opId, getOperations)).start();;
			}			
		}
	}
	
	private Boolean requiresQuery(boolean immediate, String method, String deviceName, ResourceOperation operation) {
		// if the immediate flag is set
		if (immediate) 
			return true;
		// if the resource operation method is a set
		if (method.equals("set"))
			return true;
		// if the objectCache has no values
		if (objectCache.get(deviceName, operation) == null)
			return true;
		return false;
	}
	
	private void checkVirtualObject(VirtualObject object, String objectName, String transactionId) {		
		if (object == null) {
			logger.error("Object " + objectName + " not found");
			String opId = transactions.get(transactionId).newOpId();
			completeTransaction(transactionId,opId,new ArrayList<Reading>());
			throw new NotFoundException("DeviceObject", objectName);
		}
	}
	
	private List<ResourceOperation> getResourceOperations(String deviceName, String transactionId, String commandName, String method) {
		// get this device's resources map
		List<ResourceOperation> resources = profiles.getCommandList(deviceName, commandName, method);
		
		if (resources == null) {
			logger.error("Command requested for unknown device " + deviceName);
			String opId = transactions.get(transactionId).newOpId();
			completeTransaction(transactionId,opId,new ArrayList<Reading>());
			throw new NotFoundException("ResourceOperation", commandName);
		}
		
		// get the operations for this device's object operation method
		return resources;
	}
	
	private List<ResourceOperation> retrieveGetResourceOperations(String deviceName, String commandName){
		return profiles.getCommandList(deviceName, commandName, "get");
	}
	
	private String parseArguments(String arguments, ResourceOperation operation, Device device, VirtualObject object, List<ResourceOperation> getOperations) {
		PropertyValue value = object.getProperties().getValue();
		String val = parseArg(arguments, operation, value, operation.getParameter());
		
		// if the written value is on a multiplexed handle, read the current value and apply the mask first
		if (!value.mask().equals(BigInteger.ZERO)) {
			String result = driver.processCommand("get", object, val, device, getOperations);
			val = transform.maskedValue(value, val, result);
			if (operation.getSecondary() != null) {
				for (String secondary: operation.getSecondary()) {
				  ServiceObject secondaryObject = profiles.getServiceObject(device.getName(), secondary);
					if (secondaryObject != null) {
						PropertyValue secondaryValue = secondaryObject.getProperties().getValue();
						String secondVal = parseArg(arguments, operation, secondaryValue, secondary);
						val = transform.maskedValue(secondaryValue, secondVal, "0x" + val);
					}
				}
			}
		}
		while (val.length() < value.size())
			val = "0" + val;
		return val;
	}
	
	private String parseArg(String arguments, ResourceOperation operation, PropertyValue value, String object) {
		// parse the argument string and get the "value" parameter
		JsonObject args;
		String val = null;
		JsonElement jElem = null;
		Boolean passed = true;
		
		// check for parameters from the command
		if(arguments != null){
			args = new JsonParser().parse(arguments).getAsJsonObject();
			jElem = args.get(object);
		}
		
		// if the parameter is passed from the command, use it, otherwise treat parameter as the default
		if (jElem == null || jElem.toString().equals("null")) {
			val = operation.getParameter();
			passed = false;
		} else {
			val = jElem.toString().replace("\"", "");
		}
		
		// if no value is specified by argument or parameter, take the object default from the profile
		if (val == null) {
			val = value.getDefaultValue();
			passed = false;
		}
		
		// if a mapping translation has been specified in the profile, use it
		Map<String,String> mappings = operation.getMappings();
		if (mappings != null && mappings.containsKey(val)) {
			val = mappings.get(val);
			passed = false;
		}
		
		if (!value.mask().equals(BigInteger.ZERO) && passed) {
			val = transform.format(value, val);
		}
		
		return val;
	}

	public void completeTransaction(String transactionId, String opId, List<Reading> readings) {		
		synchronized (transactions) {
			transactions.get(transactionId).finishOp(opId, readings);
			transactions.notifyAll();
		}
	}

}
