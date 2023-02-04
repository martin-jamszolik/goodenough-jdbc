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

import org.springframework.jdbc.support.rowset.SqlRowSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProposalTaskMapper implements PersistableMapper<ProposalTask> {
    private final Map<Key, Proposal> proposals = new LinkedHashMap<>();
    private final Map<Key, Task> tasks = new LinkedHashMap<>();

    @Override
    public ProposalTask mapRow(SqlRowSet rs, int rowNum) {
        var proposalTask = PersistableRowMapper.of(ProposalTask.class).mapRow(rs, rowNum);
        if (rs.getObject("pr_key") != null) {
            var prop = proposals.computeIfAbsent(Key.of("pr_key", rs.getLong("pr_key")),
                pr_key -> PersistableRowMapper.of(Proposal.class).mapRow(rs, rowNum)
            );
            proposalTask.setProposal(prop);
            prop.addTask(proposalTask);
        }
        if (rs.getObject("t_key") != null) {
            var task = tasks.computeIfAbsent(Key.of("t_key", rs.getLong("t_key")),
                t_key -> PersistableRowMapper.of(Task.class).mapRow(rs, rowNum)
            );
            proposalTask.setTask(task);
        }
        return proposalTask;
    }

    public List<Proposal> getProposals() {
        return new ArrayList<>(proposals.values());
    }
}
