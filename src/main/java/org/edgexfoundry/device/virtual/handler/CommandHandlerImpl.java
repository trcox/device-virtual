package org.edgexfoundry.device.virtual.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.edgexfoundry.device.store.DeviceStore;
import org.edgexfoundry.device.virtual.Initializer;
import org.edgexfoundry.domain.meta.Device;
import org.edgexfoundry.exception.controller.LockedException;
import org.edgexfoundry.exception.controller.NotFoundException;
import org.edgexfoundry.service.handler.CommandHandler;
import org.edgexfoundry.service.handler.ServiceHandler;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CommandHandlerImpl implements CommandHandler {

  private final EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(this.getClass());

  @Autowired
  ServiceHandler handler;

  @Autowired
  DeviceStore devices;

  @Autowired
  Initializer init;
  
  private String requestType(String method) {
    switch(method) {
    case "PUT":
    case "POST":
      return "set";
    case "GET":
      return "get";
    default:
      return "get";
    }
  }
  
  @Override
  public Map<String,String> getResponse(String deviceId, String cmd, String arguments, String method) {
    String operation = requestType(method);
    if (init.isServiceLocked()) {
      logger.error(method + " request cmd: " + cmd + " with device service locked on: " + deviceId);
      throw new LockedException(method + " request cmd: " + cmd
          + " with device service locked on: " + deviceId);
    }

    if (devices.isDeviceLocked(deviceId)) {
      logger.error(method + " request cmd: " + cmd + " with device locked on: " + deviceId);
      throw new LockedException(method + " request cmd: " + cmd + " with device locked on: " + deviceId);
    }

    Device device = devices.getDeviceById(deviceId);
    if (handler.commandExists(device, cmd, operation)) {
      return handler.executeCommand(device, cmd, arguments, operation);
    } else {
      logger.error("Command: " + cmd + " does not exist for device with id: " + deviceId);
      throw new NotFoundException("Command", cmd);
    }
  }

  @Override
  public Map<String,String> getResponses(String cmd, String arguments, String method) {
    Map<String,String> responses = new HashMap<String,String>();
    String operation = requestType(method);
    if (init.isServiceLocked()) {
      logger.error(method + " request cmd: " + cmd + " with device service locked ");
      throw new LockedException(method + " request cmd: " + cmd + " with device locked");
    }

    for (String deviceId: devices.getDevices().entrySet().stream()
        .map(d -> d.getValue().getId()).collect(Collectors.toList())) {
      if (devices.isDeviceLocked(deviceId)) {
        continue;
      }

      Device device = devices.getDeviceById(deviceId);
      if (handler.commandExists(device, cmd, operation)) {
        responses.putAll(handler.executeCommand(device, cmd, arguments, operation));
      }
    }
    return responses;
  }

}
