/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.pinot.thirdeye.anomaly.onboard.tasks;

import com.google.common.base.Preconditions;
import org.apache.pinot.thirdeye.anomaly.detection.DetectionJobScheduler;
import org.apache.pinot.thirdeye.anomaly.job.JobConstants.JobStatus;
import org.apache.pinot.thirdeye.anomaly.onboard.framework.BaseDetectionOnboardTask;
import org.apache.pinot.thirdeye.anomaly.onboard.framework.DetectionOnboardExecutionContext;
import org.apache.pinot.thirdeye.anomalydetection.alertFilterAutotune.AlertFilterAutotuneFactory;
import org.apache.pinot.thirdeye.dashboard.resources.DetectionJobResource;
import org.apache.pinot.thirdeye.datalayer.dto.AnomalyFunctionDTO;
import org.apache.pinot.thirdeye.detector.email.filter.AlertFilterFactory;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This task performs replay on anomaly functions
 */
public class FunctionReplayOnboardingTask extends BaseDetectionOnboardTask {
  private static final Logger LOG = LoggerFactory.getLogger(FunctionReplayOnboardingTask.class);
  public static final String TASK_NAME = "FunctionReplay";

  public static final String ANOMALY_FUNCTION_CONFIG = DefaultDetectionOnboardJob.ANOMALY_FUNCTION_CONFIG;
  public static final String ALERT_FILTER_FACTORY = DefaultDetectionOnboardJob.ALERT_FILTER_FACTORY;
  public static final String ALERT_FILTER_AUTOTUNE_FACTORY = DefaultDetectionOnboardJob.ALERT_FILTER_AUTOTUNE_FACTORY;
  public static final String BACKFILL_PERIOD = DefaultDetectionOnboardJob.PERIOD;
  public static final String BACKFILL_START = DefaultDetectionOnboardJob.START;
  public static final String BACKFILL_END = DefaultDetectionOnboardJob.END;
  public static final String BACKFILL_FORCE = DefaultDetectionOnboardJob.FORCE;
  public static final String BACKFILL_SPEEDUP = DefaultDetectionOnboardJob.SPEEDUP;
  public static final String BACKFILL_REMOVE_ANOMALY_IN_WINDOW = DefaultDetectionOnboardJob.REMOVE_ANOMALY_IN_WINDOW;

  public static final String DEFAULT_BACKFILL_PERIOD = "P30D";
  public static final Boolean DEFAULT_BACKFILL_FORCE = true;
  public static final Boolean DEFAULT_BACKFILL_SPEEDUP = false;
  public static final Boolean DEFAULT_BACKFILL_REMOVE_ANOMALY_IN_WINDOW = false;

  private DetectionJobScheduler detectionJobScheduler;

  public FunctionReplayOnboardingTask() {
    super(TASK_NAME);
  }

  /**
   * Executes the task. To fail this task, throw exceptions. The job executor will catch the exception and store
   * it in the message in the execution status of this task.
   */
  @Override
  public void run() {

    try {
      Response response = initDetectionJob();
      Map<Long, Long> functionIdToJobIdMap = (Map<Long, Long>) response.getEntity();
      for (long jobId : functionIdToJobIdMap.values()) {
        JobStatus jobStatus = detectionJobScheduler.waitForJobDone(jobId);
        if (JobStatus.FAILED.equals(jobStatus) || JobStatus.TIMEOUT.equals(jobStatus)) {
          throw new InterruptedException("Get Job Status: " + jobStatus);
        }
      }

    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public Response initDetectionJob() throws Exception{
    Configuration taskConfiguration = taskContext.getConfiguration();
    DetectionOnboardExecutionContext executionContext = taskContext.getExecutionContext();

    Preconditions.checkNotNull(executionContext.getExecutionResult(ALERT_FILTER_FACTORY));
    Preconditions.checkNotNull(executionContext.getExecutionResult(ALERT_FILTER_AUTOTUNE_FACTORY));

    AlertFilterFactory alertFilterFactory = (AlertFilterFactory) executionContext.getExecutionResult(ALERT_FILTER_FACTORY);
    AlertFilterAutotuneFactory alertFilterAutotuneFactory = (AlertFilterAutotuneFactory)
        executionContext.getExecutionResult(ALERT_FILTER_AUTOTUNE_FACTORY);

    Preconditions.checkNotNull(alertFilterFactory);
    Preconditions.checkNotNull(alertFilterAutotuneFactory);

    detectionJobScheduler = new DetectionJobScheduler();
    DetectionJobResource detectionJobResource = new DetectionJobResource(detectionJobScheduler,
        alertFilterFactory, alertFilterAutotuneFactory);
    AnomalyFunctionDTO anomalyFunction = (AnomalyFunctionDTO) executionContext.getExecutionResult(ANOMALY_FUNCTION_CONFIG);
    long functionId = anomalyFunction.getId();
    Period backfillPeriod = Period.parse(taskConfiguration.getString(BACKFILL_PERIOD, DEFAULT_BACKFILL_PERIOD));
    DateTime start = DateTime.parse(taskConfiguration.getString(BACKFILL_START, DateTime.now().minus(backfillPeriod).toString()));
    DateTime end = DateTime.parse(taskConfiguration.getString(BACKFILL_END, DateTime.now().toString()));
    executionContext.setExecutionResult(BACKFILL_START, start);
    executionContext.setExecutionResult(BACKFILL_END, end);

    Response response = null;
    try {
      LOG.info("Running replay task for {} from {} to {}", anomalyFunction, start, end);
      response = detectionJobResource.generateAnomaliesInRange(functionId, start.toString(), end.toString(),
          Boolean.toString(taskConfiguration.getBoolean(BACKFILL_FORCE, DEFAULT_BACKFILL_FORCE)),
          taskConfiguration.getBoolean(BACKFILL_SPEEDUP, DEFAULT_BACKFILL_SPEEDUP),
          taskConfiguration.getBoolean(BACKFILL_REMOVE_ANOMALY_IN_WINDOW, DEFAULT_BACKFILL_REMOVE_ANOMALY_IN_WINDOW));
    } catch (Exception e){
      throw new IllegalStateException(String.format("Unable to create detection job for %d from %s to %s\n%s",
          functionId, start.toString(), end.toString(), ExceptionUtils.getStackTrace(e)));
    }
    return response;
  }
}
