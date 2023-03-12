
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

import org.viablespark.persistence.dsl.Named;
import org.viablespark.persistence.dsl.PrimaryKey;
import org.viablespark.persistence.dsl.Ref;
import org.viablespark.persistence.dsl.Skip;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


@Named("est_proposal")
@PrimaryKey("pr_key")
public class Proposal extends Model {

    private String propName;
    private Date propDate;
    private Integer distanceToFollow;

    private LocalDate submitDeadline;
    private String propId;
    private String skipMeIamWorthless;

    private Contractor contractor;

    private List<ProposalTask> tasks = new ArrayList<>();

    public Proposal() {
    }

    public Proposal(String key, Long id) {
        super.setRefs(Key.of(key, id));
    }

    @Named("proposal_name")
    public String getPropName() {
        return propName;
    }

    public void setPr_key(Long id) {
        setRefs(Key.of("pr_key", id));
    }

    public void setPropName(String propName) {
        this.propName = propName;
    }

    public Date getPropDate() {
        return propDate;
    }

    public void setPropDate(Date propDate) {
        this.propDate = propDate;
    }

    @Named("dist")
    public Integer getDistance() {
        return distanceToFollow;
    }

    public void setDistance(Integer distanceToFollow) {
        this.distanceToFollow = distanceToFollow;
    }

    public LocalDate getSubmitDeadline() {
        return submitDeadline;
    }

    public void setSubmitDeadline(LocalDate submitDeadline) {
        this.submitDeadline = submitDeadline;
    }

    public String getPropId() {
        return propId;
    }

    public void setPropId(String propId) {
        this.propId = propId;
    }

    @Skip
    public String getSkipMeIamWorthless() {
        return skipMeIamWorthless;
    }

    public void setSkipMeIamWorthless(String skipMeIamWorthless) {
        this.skipMeIamWorthless = skipMeIamWorthless;
    }


    @Ref
    public Contractor getContractor() {
        return contractor;
    }

    public void setContractor(Contractor contractor) {
        this.contractor = contractor;
    }

    @Skip
    public List<ProposalTask> getTasks() {
        return tasks;
    }

    public void setTasks(List<ProposalTask> tasks) {
        this.tasks = tasks;
    }
    public void addTask(ProposalTask task){
        tasks.add(task);
    }
}
