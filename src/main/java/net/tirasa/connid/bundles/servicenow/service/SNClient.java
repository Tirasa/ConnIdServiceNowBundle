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
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.core.Response;
import net.tirasa.connid.bundles.servicenow.SNConnectorConfiguration;
import net.tirasa.connid.bundles.servicenow.dto.BatchRequest;
import net.tirasa.connid.bundles.servicenow.dto.MembershipResource;
import net.tirasa.connid.bundles.servicenow.dto.PagedResults;
import net.tirasa.connid.bundles.servicenow.dto.Resource;
import net.tirasa.connid.bundles.servicenow.utils.SNUtils;
import org.apache.cxf.jaxrs.client.WebClient;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;

public class SNClient extends SNService {

    private static final Log LOG = Log.getLog(SNClient.class);

    public SNClient(final SNConnectorConfiguration config) {
        super(config);
    }

    /**
     *
     * @param type
     * @return List of Resources
     */
    public PagedResults<Resource> getResources(final ResourceTable type) {
        return doGetResources(getTableWebClient(type, null), Resource.class);
    }

    /**
     *
     * @param type
     * @param startIndex
     * @param count
     * @param backward
     * @return Paged list of Resources
     */
    public PagedResults<Resource> getResources(final ResourceTable type,
            final Integer startIndex,
            final Integer count,
            final Boolean backward) {
        Map<String, String> params = new HashMap<>();
        params.put("sysparm_offset", String.valueOf(startIndex));
        if (count != null) {
            params.put("sysparm_limit", String.valueOf(count));
        }
        if (backward) {
            params.put("rel", "prev");
        } else {
            params.put("rel", "next");
        }
        return doGetResources(getTableWebClient(type, params), Resource.class);
    }

    /**
     *
     * @param type
     * @param filterQuery to filter results
     * @return Filtered list of Resources
     */
    public PagedResults<Resource> getResources(final ResourceTable type, final String filterQuery) {
        Map<String, String> params = new HashMap<>();
        params.put("sysparm_query", filterQuery);
        return doGetResources(getTableWebClient(type, params), Resource.class);
    }

    /**
     *
     * @param type
     * @param filterQuery to filter results
     * @return Filtered list of Resources
     */
    public PagedResults<MembershipResource> getMembershipResources(final ResourceTable type, final String filterQuery) {
        Map<String, String> params = new HashMap<>();
        params.put("sysparm_query", filterQuery);
        return doGetResources(getTableWebClient(type, params), MembershipResource.class);
    }

    /**
     *
     * @param type
     * @param filterQuery
     * @param startIndex
     * @param count
     * @param backward
     * @return Paged list of Resources
     */
    public PagedResults<Resource> getResources(
            final ResourceTable type,
            final String filterQuery,
            final Integer startIndex,
            final Integer count,
            final Boolean backward) {

        Map<String, String> params = new HashMap<>();
        params.put("sysparm_query", filterQuery);
        params.put("sysparm_offset", String.valueOf(startIndex));
        if (count != null) {
            params.put("sysparm_limit", String.valueOf(count));
        }
        if (backward) {
            params.put("rel", "prev");
        } else {
            params.put("rel", "next");
        }
        return doGetResources(getTableWebClient(type, params), Resource.class);
    }

    /**
     *
     * @param type
     * @param id
     * @return Resource with resource id
     */
    public Resource getResource(final ResourceTable type, final String id) {
        Resource resource = null;
        JsonNode node = doGet(getTableWebClient(type, null).path(id));
        if (node == null) {
            SNUtils.handleGeneralError("While retrieving Resource from service");
        }

        try {
            resource = SNUtils.MAPPER.readValue(node.toString(), Resource.class);
        } catch (IOException ex) {
            LOG.error(ex, "While converting from JSON to Resource");
        }

        if (resource == null) {
            SNUtils.handleGeneralError("While retrieving Resource from service after create");
        }

        return resource;
    }

    /**
     *
     * @param type
     * @param resource
     * @return Created Resource
     */
    public Resource createResource(final ResourceTable type, final Resource resource) {
        doCreate(resource, getTableWebClient(type, null));
        return resource;
    }

    /**
     *
     * @param type
     * @param resource
     * @return Update Resource
     */
    public Resource updateResource(final ResourceTable type, final Resource resource) {
        if (StringUtil.isBlank(resource.getSysId())) {
            SNUtils.handleGeneralError("Missing required Resource id attribute for update");
        }

        Resource updated = null;
        JsonNode node = doUpdate(resource, getTableWebClient(type, null).path(resource.getSysId()));
        if (node == null) {
            SNUtils.handleGeneralError("While running update on service");
        }

        try {
            updated = SNUtils.MAPPER.readValue(node.toString(), Resource.class);
        } catch (IOException ex) {
            LOG.error(ex, "While converting from JSON to Resource");
        }

        if (updated == null) {
            SNUtils.handleGeneralError("While retrieving Resource from service after update");
        }

        return updated;
    }

    /**
     *
     * @param type
     * @param id
     */
    public void deleteResource(final ResourceTable type, final String id) {
        WebClient webClient = getTableWebClient(type, null).path(id);
        doDelete(id, webClient);
    }

    /**
     *
     *
     * @return true if service status is OK, false otherwise
     */
    public boolean testService() {
        return SNClient.this.getResources(ResourceTable.sys_user, 0, 1, false) != null;
    }

    public void executeBatch(final BatchRequest batchRequest) {
        WebClient webClient = getOpWebClient(BATCH_OP, null);

        LOG.ok("BATCH: {0}", webClient.getCurrentURI());
        String payload = null;

        try {
            payload = SNUtils.MAPPER.writeValueAsString(batchRequest);
            Response response = webClient.post(payload);
            String responseAsString = checkServiceErrors(response);

            JsonNode result = SNUtils.MAPPER.readTree(responseAsString);
            if (result.hasNonNull(RESPONSE_BATCH_REQUEST_ID) && result.hasNonNull(RESPONSE_SERVICED_REQUESTS)) {
                LOG.ok("Batch request successfully executed {0}: ", responseAsString);
            } else {
                LOG.error("Batch request error with payload {0}: ", payload);
                SNUtils.handleGeneralError("While executing batch request - Response: " + responseAsString);
            }
        } catch (IOException ex) {
            LOG.error("BATCH payload {0}: ", payload);
            SNUtils.handleGeneralError("While creating Resource", ex);
        }

    }

    private <T extends Resource> PagedResults<T> doGetResources(final WebClient webClient, Class<T> clazz) {
        PagedResults<T> resources = null;
        JsonNode node = doGet(webClient);
        if (node == null) {
            SNUtils.handleGeneralError("While retrieving Resources from service");
        }

        try {
            resources = SNUtils.MAPPER.readValue(node.toString(),
                    SNUtils.MAPPER.getTypeFactory().constructParametricType(PagedResults.class, clazz));
        } catch (IOException ex) {
            LOG.error(ex, "While converting from JSON to Resources");
        }

        if (resources == null) {
            SNUtils.handleGeneralError("While retrieving Resources from service");
        }

        return resources;
    }
}
