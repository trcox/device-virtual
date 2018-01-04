package org.edgexfoundry.device.virtual.handler;

import org.edgexfoundry.controller.ScheduleClient;
import org.edgexfoundry.controller.ScheduleEventClient;
import org.edgexfoundry.device.scheduling.Scheduler;
import org.edgexfoundry.domain.meta.ActionType;
import org.edgexfoundry.domain.meta.CallbackAlert;
import org.edgexfoundry.domain.meta.Schedule;
import org.edgexfoundry.domain.meta.ScheduleEvent;
import org.edgexfoundry.service.handler.SchedulerCallbackHandler;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ScheduleCallbackHandlerImpl implements SchedulerCallbackHandler {
  
  private final EdgeXLogger logger =
      EdgeXLoggerFactory.getEdgeXLogger(this.getClass());

  @Autowired
  private Scheduler scheduler;

  @Autowired
  private ScheduleClient scheduleClient;

  @Autowired
  private ScheduleEventClient scheduleEventClient;
  
  @Override
  public boolean handlePut(CallbackAlert data) {
    ActionType action = data.getType();
    switch (action) {
      case SCHEDULE:
        try {
          Schedule schedule = scheduleClient.schedule(data.getId());
          if (schedule != null) {
            scheduler.updateScheduleContext(schedule);
          }
        } catch (Exception e) {
          logger.error("failed to put schedule " + data.getId() + " " + e);
          return false;
        }

        break;
      case SCHEDULEEVENT:
        try {
          ScheduleEvent scheduleEvent = scheduleEventClient.scheduleEvent(data.getId());
          if (scheduleEvent != null) {
            scheduler.updateScheduleEventInScheduleContext(scheduleEvent);
          }
        } catch (Exception e) {
          logger.error("failed to put schedule event " + data.getId() + " " + e);
          return false;
        }

        break;
      default:
        break;
    }

    return true;
  }

  @Override
  public boolean handlePost(CallbackAlert data) {
    ActionType action = data.getType();
    switch (action) {
      case SCHEDULE:
        try {
          Schedule schedule = scheduleClient.schedule(data.getId());
          if (schedule != null) {
            scheduler.createScheduleContext(schedule);
          }
        } catch (Exception e) {
          logger.error("failed to post schedule " + data.getId() + " " + e);
          return false;
        }

        break;
      case SCHEDULEEVENT:
        try {
          ScheduleEvent scheduleEvent = scheduleEventClient.scheduleEvent(data.getId());
          if (scheduleEvent != null) {
            scheduler.addScheduleEventToScheduleContext(scheduleEvent);
          }
        } catch (Exception e) {
          logger.error("failed to post schedule event " + data.getId() + " " + e);
          return false;
        }

        break;
      default:
        break;
    }
    return true;
  }

  @Override
  public boolean handleDelete(CallbackAlert data) {
    ActionType action = data.getType();
    switch (action) {
      case SCHEDULE:
        try {
          scheduler.removeScheduleById(data.getId());
        } catch (Exception e) {
          logger.error("failed to delete schedule " + data.getId() + " " + e);
          return false;
        }

        break;
      case SCHEDULEEVENT:
        try {
          scheduler.removeScheduleEventById(data.getId());
        } catch (Exception e) {
          logger.error("failed to delete schedule " + data.getId() + " " + e);
          return false;
        }

        break;
      default:
        break;
    }
    return true;
  }

}
