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
package net.tirasa.connid.bundles.servicenow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import net.tirasa.connid.bundles.servicenow.dto.Resource;
import net.tirasa.connid.bundles.servicenow.utils.SNAttributes;
import net.tirasa.connid.bundles.servicenow.utils.SNUtils;
import org.apache.cxf.jaxrs.client.WebClient;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;

public class SNService {

    private static final Log LOG = Log.getLog(SNService.class);

    private final String baseAddress;

    private final String username;

    private final String password;

    public final static String RESPONSE_RESULT = "result";

    public final static String RESPONSE_HEADER_TOTAL_COUNT = "x-total-count";

    public enum ResourceTable {
        sys_user,
        sys_user_group

    }

    public SNService(final String baseAddress,
            final String username,
            final String password) {
        this.baseAddress = baseAddress;
        this.username = username;
        this.password = password;
    }

    public WebClient getWebclient(final ResourceTable table, final Map<String, String> params) {
        WebClient webClient = WebClient
                .create(baseAddress,
                        username,
                        password,
                        null)
                .accept(MediaType.APPLICATION_JSON)
                .type(MediaType.APPLICATION_JSON)
                .path("/api/now/table/")
                .path(table);

        if (params != null) {
            for (Entry<String, String> entry : params.entrySet()) {
                webClient.query(entry.getKey(), entry.getValue()); // will encode parameter
            }
        }

        return webClient;
    }

    public JsonNode doGet(final WebClient webClient) {
        LOG.ok("webClient current URL : {0}", webClient.getCurrentURI());
        JsonNode result = null;

        try {
            Response response = webClient.get();
            checkServiceErrors(response);
            
            result = SNUtils.MAPPER.readTree(response.readEntity(String.class));
            if (result.get(RESPONSE_RESULT).isArray()) {
                final String totalCount = response.getHeaderString(RESPONSE_HEADER_TOTAL_COUNT);
                if (StringUtil.isNotBlank(totalCount)) {
                    ((ObjectNode) result).put("totalCount", String.valueOf(totalCount));
                }
            } else {
                result = result.get(RESPONSE_RESULT);
            }
        } catch (IOException ex) {
            LOG.error(ex, "While retrieving data from ServiceNow");
        }

        return result;
    }

    protected void doCreate(final Resource resource, final WebClient webClient) {
        LOG.ok("webClient current URL : {0}", webClient.getCurrentURI());

        try {
            String payload = SNUtils.MAPPER.writeValueAsString(resource);
            Response response = webClient.post(payload);
            checkServiceErrors(response);
            
            String value = SNAttributes.RESOURCE_ATTRIBUTE_ID;
            String responseAsString = response.readEntity(String.class);
            JsonNode result = SNUtils.MAPPER.readTree(responseAsString);
            if (result.hasNonNull(RESPONSE_RESULT)
                    && result.get(RESPONSE_RESULT).hasNonNull(value)) {
                resource.setSysId(result.get(RESPONSE_RESULT).get(value).textValue());
            } else {
                SNUtils.handleGeneralError(
                        "While getting " + value + " value for created Resource - Response : " + responseAsString);
            }
        } catch (IOException ex) {
            SNUtils.handleGeneralError("While creating Resource", ex);
        }
    }

    protected JsonNode doUpdate(final Resource resource, final WebClient webClient) {
        LOG.ok("webClient current URL : {0}", webClient.getCurrentURI());
        JsonNode result = null;

        WebClient.getConfig(webClient).getRequestContext().put("use.async.http.conduit", true);
        try {
            String payload = SNUtils.MAPPER.writeValueAsString(resource);
            Response response = webClient.invoke("PATCH", payload);
            checkServiceErrors(response);
            
            String responseAsString = response.readEntity(String.class);
            result = SNUtils.MAPPER.readTree(responseAsString);
            if (result.hasNonNull(RESPONSE_RESULT)) {
                result = result.get(RESPONSE_RESULT);
            } else {
                SNUtils.handleGeneralError(
                        "While updating " + resource.getSysId() + " Resource - Response : " + responseAsString);
            }
        } catch (IOException ex) {
            SNUtils.handleGeneralError("While updating Resource", ex);
        }

        return result;
    }

    protected void doDelete(final String userId, final WebClient webClient) {
        LOG.ok("webClient current URL : {0}", webClient.getCurrentURI());
        int status = webClient.delete().getStatus();
        if (status != Response.Status.NO_CONTENT.getStatusCode() && status != Response.Status.OK.getStatusCode()) {
            throw new NoSuchEntityException(userId);
        }
    }

    private void checkServiceErrors(final Response response) {
        if (response == null) {
            SNUtils.handleGeneralError("While executing request - no response");
        } else if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
            throw new NoSuchEntityException(response.readEntity(String.class));
        } else if (response.getStatus() != Response.Status.OK.getStatusCode()
                && response.getStatus() != Response.Status.ACCEPTED.getStatusCode()
                && response.getStatus() != Response.Status.CREATED.getStatusCode()) {
            SNUtils.handleGeneralError("While executing request: " + response.readEntity(String.class));
        }
    }

}
