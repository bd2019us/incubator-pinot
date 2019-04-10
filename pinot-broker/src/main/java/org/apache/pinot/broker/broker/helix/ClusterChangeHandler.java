/**
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
package org.apache.pinot.broker.broker.helix;

import org.apache.helix.HelixConstants;
import org.apache.pinot.annotations.InterfaceAudience;
import org.apache.pinot.annotations.InterfaceStability;


/**
 * Handles cluster changes such as external view changes, instance config changes, live instance changes etc.
 */
@InterfaceAudience.Public
@InterfaceStability.Evolving
public interface ClusterChangeHandler {

  /**
   * Processes the cluster change of the given type (e.g. EXTERNAL_VIEW, INSTANCE_CONFIG, LIVE_INSTANCE).
   */
  void processClusterChange(HelixConstants.ChangeType changeType);
}
