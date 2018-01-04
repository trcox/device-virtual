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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.edgexfoundry.controller.DeviceClient;
import org.edgexfoundry.controller.EventClient;
import org.edgexfoundry.device.domain.ResponseObject;
import org.edgexfoundry.device.store.impl.DeviceStoreImpl;
import org.edgexfoundry.domain.core.Event;
import org.edgexfoundry.domain.core.Reading;
import org.edgexfoundry.domain.meta.Device;
import org.edgexfoundry.domain.meta.OperatingState;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;

@Service
public class CoreDataMessageHandler {


	private final EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(this.getClass());

	@Value("${service.connectRetries}")
	private int retries;
	@Value("${service.connectWait}")
	private long delay;

	@Autowired
	private DeviceClient deviceClient;
	
	@Autowired
	private EventClient eventClient;
	
	@Autowired
	private DeviceStoreImpl devices;
	
	public Reading buildReading(String key, String value, String deviceName) {
    Reading reading = new Reading();
    reading.setName(key);
    reading.setValue(value);
    reading.setDevice(deviceName);
    return reading;
  }

  private Event buildEvent(String deviceName, List<Reading> readings) {
    Event event = new Event(deviceName);
    event.setReadings(readings);
    return event;
  }

  private boolean sendEvent(Event event, int attempt) {
    if (retries == 0 || attempt < retries) {
      if (event != null) {
        try {
          eventClient.add(event);
          return true;
        } catch (Exception e) {
          // something happened trying to send to
          // core data - likely that the service
          // is down.
          logger.debug("Problem sending event for " + event.getDevice()
              + " to core data.  Retrying (attempt " + (attempt + 1) + ")...");
          try {
            Thread.sleep(delay);
          } catch (InterruptedException interrupt) {
            logger.debug("Event send delay interrupted");
            interrupt.printStackTrace();
          }

          return sendEvent(event, ++attempt);
        }
      }
    }
    return false;
  }

  private void updateLastConnected(String deviceName) {
    long currentTime = Calendar.getInstance().getTimeInMillis();
    Device device = devices.getDevice(deviceName);
    if (device != null && ((currentTime - 60000) > device.getLastConnected())) {
      deviceClient.updateLastConnected(device.getId(), currentTime);
      if (device.getOperatingState().equals(OperatingState.DISABLED)) {
        deviceClient.updateOpState(device.getId(), OperatingState.ENABLED.name());
      }
    }
  }

  public List<ResponseObject> sendCoreData(String deviceName, List<Reading> readings) {

    try {
      List<ResponseObject> resps = new ArrayList<>();
      logger.debug("readings: " + readings);
      for (Reading reading: readings) {
        ResponseObject resp = new ResponseObject(reading.getName(), reading.getValue());
        resps.add(resp);
      }

      new Thread(() -> executeSendData(deviceName, readings)).start();
      
      return resps;
    } catch (Exception e) {
      logger.error("Cannot push the readings to Coredata " + e.getMessage());
    }

    return new ArrayList<ResponseObject>();
  }
  
  private void executeSendData(String deviceName, List<Reading> readings) {
    boolean success = sendEvent(buildEvent(deviceName, readings), 0);
    if (success) {
      updateLastConnected(deviceName);
    } else {
      
      if (devices.getDevice(deviceName).getOperatingState().equals(OperatingState.ENABLED)) {
        deviceClient.updateOpStateByName(deviceName, OperatingState.DISABLED.name());
      }

      logger.error("Could not send event to core data for "
          + deviceName + ".  Check core data service");
    }
  }
}
