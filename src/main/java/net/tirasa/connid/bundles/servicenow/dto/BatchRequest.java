/**
 * Copyright © 2018 ConnId (connid-dev@googlegroups.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.tirasa.connid.bundles.servicenow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class BatchRequest implements Serializable {

    @JsonProperty("batch_request_id")
    private String batchRequestId;

    @JsonProperty("rest_requests")
    private List<BatchOperation> requests = new ArrayList<>();

    public BatchRequest(final String batchRequestId) {
        this.batchRequestId = batchRequestId;
    }

    public String getBatchRequestId() {
        return batchRequestId;
    }

    public void setBatchRequestId(final String batchRequestId) {
        this.batchRequestId = batchRequestId;
    }

    public List<BatchOperation> getRequests() {
        return requests;
    }

    @Override
    public String toString() {
        return "BatchRequest{" + "requests=" + requests + '}';
    }
}
