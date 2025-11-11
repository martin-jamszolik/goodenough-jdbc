/*
 * Copyright (c) 2023 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.viablespark.persistence;

import org.viablespark.persistence.dsl.PrimaryKey;
import org.viablespark.persistence.dsl.Ref;

@PrimaryKey("t_key")
public class ProposalTask extends Model {

  private Task task;
  private Proposal proposal;

  @Ref
  public Task getTask() {
    return task;
  }

  public void setTask(Task task) {
    this.task = task;
  }

  @Ref
  public Proposal getProposal() {
    return proposal;
  }

  public void setProposal(Proposal proposal) {
    this.proposal = proposal;
  }
}
