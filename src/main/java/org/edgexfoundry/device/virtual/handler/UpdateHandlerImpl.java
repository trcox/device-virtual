package org.edgexfoundry.device.virtual.handler;

import org.edgexfoundry.device.store.DeviceStore;
import org.edgexfoundry.device.store.WatcherStore;
import org.edgexfoundry.service.handler.ServiceHandler;
import org.edgexfoundry.service.handler.UpdateHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UpdateHandlerImpl implements UpdateHandler {
  
  @Autowired
  private WatcherStore watchers;

  @Autowired
  private DeviceStore devices;
  
  @Autowired
  private ServiceHandler handler;

  public boolean addDevice(String deviceId) {
    return devices.add(deviceId, handler);
  }

  public boolean updateDevice(String deviceId) {
    return devices.update(deviceId, handler);
  }

  public boolean deleteDevice(String deviceId) {
    return devices.remove(deviceId, handler);
  }

  public boolean addWatcher(String provisionWatcher) {
    return watchers.add(provisionWatcher);
  }

  public boolean removeWatcher(String provisionWatcher) {
    return watchers.remove(provisionWatcher);
  }

  public boolean updateWatcher(String provisionWatcher) {
    return watchers.update(provisionWatcher);
  }

  public boolean updateProfile(String profileId) {
    return devices.updateProfile(profileId, handler) && watchers.updateProfile(profileId);
  }
}
