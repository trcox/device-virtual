package org.edgexfoundry.device.virtual.domain;

import org.edgexfoundry.device.domain.ServiceObjectFactory;
import org.edgexfoundry.domain.meta.DeviceObject;
import org.springframework.stereotype.Service;

@Service
public class VirtualObjectFactory implements ServiceObjectFactory {

	@Override
	public VirtualObject createServiceObject(DeviceObject object) {
		return new VirtualObject(object);
	}

}
