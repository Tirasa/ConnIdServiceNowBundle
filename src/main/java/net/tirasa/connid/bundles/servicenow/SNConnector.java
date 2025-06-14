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
package net.tirasa.connid.bundles.servicenow;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.ws.rs.HttpMethod;
import net.tirasa.connid.bundles.servicenow.dto.BatchOperation;
import net.tirasa.connid.bundles.servicenow.dto.BatchRequest;
import net.tirasa.connid.bundles.servicenow.dto.PagedResults;
import net.tirasa.connid.bundles.servicenow.dto.Resource;
import net.tirasa.connid.bundles.servicenow.service.SNClient;
import net.tirasa.connid.bundles.servicenow.service.SNService;
import net.tirasa.connid.bundles.servicenow.utils.SNAttributes;
import net.tirasa.connid.bundles.servicenow.utils.SNUtils;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.common.security.SecurityUtil;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.AttributesAccessor;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.PredefinedAttributes;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsIgnoreCaseFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.SearchResultsHandler;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.DeleteOp;
import org.identityconnectors.framework.spi.operations.SchemaOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.TestOp;
import org.identityconnectors.framework.spi.operations.UpdateOp;

@ConnectorClass(displayNameKey = "ServiceNowConnector.connector.display",
        configurationClass = SNConnectorConfiguration.class)
public class SNConnector implements
        Connector, CreateOp, DeleteOp, SchemaOp, SearchOp<Filter>, TestOp, UpdateOp {

    private static final List<Map<String, String>> DEFAULT_HTTP_HEADERS =
            List.of(Map.of("name", "Content-Type", "value", "application/json"));

    private SNConnectorConfiguration configuration;

    private Schema schema;

    private SNClient client;

    private static final Log LOG = Log.getLog(SNConnector.class);

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public void init(Configuration configuration) {
        this.configuration = (SNConnectorConfiguration) configuration;
        this.configuration.validate();

        client = new SNClient(this.configuration);

        LOG.ok("Connector {0} successfully inited", getClass().getName());
    }

    @Override
    public void dispose() {
        configuration = null;
    }

    @Override
    public void test() {
        if (configuration != null) {
            if (client != null && client.testService()) {
                LOG.ok("Test was successfull");
            } else {
                SNUtils.handleGeneralError("Test error. Problems with client service");
            }
        } else {
            LOG.error("Test error. No instance of the configuration class");
        }
    }

    @Override
    public Schema schema() {
        if (schema == null) {
            schema = SNAttributes.buildSchema();
        }
        return schema;
    }

    @Override
    public FilterTranslator<Filter> createFilterTranslator(
            final ObjectClass objectClass,
            final OperationOptions options) {

        return new FilterTranslator<Filter>() {

            @Override
            public List<Filter> translate(final Filter filter) {
                return Collections.singletonList(filter);
            }
        };
    }

    @Override
    public void executeQuery(
            final ObjectClass objectClass,
            final Filter query,
            final ResultsHandler handler,
            final OperationOptions options) {

        Attribute key = null;
        if (query instanceof EqualsFilter || query instanceof EqualsIgnoreCaseFilter) {
            key = ((AttributeFilter) query).getAttribute();
        }

        Set<String> attributesToGet = new HashSet<>();
        if (options.getAttributesToGet() != null) {
            attributesToGet.addAll(Arrays.asList(options.getAttributesToGet()));
        }

        SNService.ResourceTable type = setResourceType(objectClass);

        if (ObjectClass.ACCOUNT.equals(objectClass) || ObjectClass.GROUP.equals(objectClass)) {
            if (key == null) {
                List<Resource> resources = null;
                int remainingResults = -1;
                int pagesSize = Optional.ofNullable(options.getPageSize()).orElse(-1);
                String cookie = options.getPagedResultsCookie();

                try {
                    if (pagesSize != -1) {
                        if (StringUtil.isNotBlank(cookie)) {
                            PagedResults<Resource> pagedResult =
                                    client.getResources(type, Integer.valueOf(cookie), pagesSize, false);
                            resources = pagedResult.getResult();

                            cookie = resources.size() >= pagesSize
                                    ? String.valueOf(Integer.parseInt(cookie) + resources.size())
                                    : null;
                        } else {
                            PagedResults<Resource> pagedResult = client.getResources(type, 0, pagesSize, false);
                            resources = pagedResult.getResult();

                            cookie = resources.size() >= pagesSize
                                    ? String.valueOf(resources.size())
                                    : null;
                        }
                    } else {
                        resources = client.getResources(type).getResult();
                    }
                } catch (Exception e) {
                    SNUtils.wrapGeneralError("While getting Resources!", e);
                }

                for (Resource resource : resources) {
                    handler.handle(fromResource(resource, objectClass, attributesToGet));
                }

                if (handler instanceof SearchResultsHandler) {
                    ((SearchResultsHandler) handler).handleResult(new SearchResult(cookie, remainingResults));
                }
            } else {
                Resource result = null;
                if (Uid.NAME.equals(key.getName()) || SNAttributes.RESOURCE_ATTRIBUTE_ID.equals(key.getName())) {
                    try {
                        result = client.getResource(type, AttributeUtil.getAsStringValue(key));
                    } catch (Exception e) {
                        SNUtils.wrapGeneralError("While getting Resource : "
                                + key.getName() + " - " + AttributeUtil.getAsStringValue(key), e);
                    }
                } else if (Name.NAME.equals(key.getName())) {
                    try {
                        List<Resource> resources =
                                client.getResources(type, "name=" + AttributeUtil.getAsStringValue(key) + "")
                                        .getResult();
                        if (resources.isEmpty() && type.equals(SNService.ResourceTable.sys_user)) {
                            resources = client.getResources(type, "user_name=" + AttributeUtil.getAsStringValue(key))
                                    .getResult();
                        }
                        if (!resources.isEmpty()) {
                            result = resources.get(0);
                        }
                    } catch (Exception e) {
                        SNUtils.wrapGeneralError("While getting Resource : "
                                + key.getName() + " - " + AttributeUtil.getAsStringValue(key), e);
                    }
                }
                if (result != null) {
                    handler.handle(fromResource(result, objectClass, attributesToGet));
                }
            }
        } else {
            LOG.warn("Search of type {0} is not supported", objectClass.getObjectClassValue());
            throw new UnsupportedOperationException("Search of type"
                    + objectClass.getObjectClassValue() + " is not supported");
        }
    }

    @Override
    public Uid create(ObjectClass objectClass, Set<Attribute> createAttributes, OperationOptions options) {
        LOG.ok("Connector CREATE");

        if (createAttributes == null || createAttributes.isEmpty()) {
            SNUtils.handleGeneralError("Set of Attributes value is null or empty");
        }

        final AttributesAccessor accessor = new AttributesAccessor(createAttributes);
        SNService.ResourceTable type = setResourceType(objectClass);

        if (ObjectClass.ACCOUNT.equals(objectClass) || ObjectClass.GROUP.equals(objectClass)) {
            Resource resource = new Resource();
            String username = accessor.findString(SNAttributes.USER_ATTRIBUTE_USERNAME);
            String name = accessor.findString(SNAttributes.RESOURCE_ATTRIBUTE_NAME);
            if (StringUtil.isBlank(username) && StringUtil.isBlank(name)) {
                name = accessor.findString(Name.NAME);
            }
            GuardedString password = accessor.findGuardedString(OperationalAttributes.PASSWORD_NAME);
            Attribute status = accessor.find(OperationalAttributes.ENABLE_NAME);

            try {
                resource.setName(name);
                resource.setUserName(username);

                if (password == null) {
                    LOG.ok("No password attribute");
                } else {
                    resource.setUserPassword(SecurityUtil.decrypt(password));
                }

                if (status == null
                        || status.getValue() == null
                        || status.getValue().isEmpty()) {
                    LOG.warn("{0} attribute value not correct or not found, won't handle Resource status",
                            OperationalAttributes.ENABLE_NAME);
                } else {
                    resource.setActive(status.getValue().get(0).toString());
                }

                resource.fromAttributes(createAttributes, configuration.getBaseAddress());

                client.createResource(type, resource);
            } catch (Exception e) {
                SNUtils.wrapGeneralError("Could not create Resource : " + username, e);
            }

            // also manage memberships
            BatchRequest batchRequest = new BatchRequest(UUID.randomUUID().toString());
            AtomicInteger counter = new AtomicInteger(1);
            Optional.ofNullable(AttributeUtil.find(PredefinedAttributes.GROUPS_NAME, createAttributes))
                    .ifPresent(groupsAttr -> {
                        groupsAttr.getValue().forEach(group -> {
                            try {
                                batchRequest.getRequests().add(new BatchOperation.Builder().id(
                                        String.valueOf(counter.getAndIncrement()))
                                        .url("/api/now/table/" + SNService.ResourceTable.sys_user_grmember.name())
                                        .headers(DEFAULT_HTTP_HEADERS)
                                        .method(HttpMethod.POST).body(Map.of("user", resource.getSysId(), "group",
                                        group.toString())).build());
                            } catch (Exception e) {
                                SNUtils.wrapGeneralError("Could not create user-group memberships : " + username, e);
                            }
                        });
                    });
            if (!batchRequest.getRequests().isEmpty()) {
                try {
                    client.executeBatch(batchRequest);
                } catch (Exception e) {
                    SNUtils.wrapGeneralError("While executing batch to user-group memberships : " + username, e);
                }
            }

            return new Uid(resource.getSysId());

        } else {
            LOG.warn("Create of type {0} is not supported", objectClass.getObjectClassValue());
            throw new UnsupportedOperationException("Create of type"
                    + objectClass.getObjectClassValue() + " is not supported");
        }
    }

    @Override
    public void delete(ObjectClass objectClass, Uid uid, OperationOptions options) {
        LOG.ok("Connector DELETE");

        if (StringUtil.isBlank(uid.getUidValue())) {
            LOG.error("Uid not provided or empty ");
            throw new InvalidAttributeValueException("Uid value not provided or empty");
        }

        if (objectClass == null) {
            LOG.error("Object value not provided {0} ", objectClass);
            throw new InvalidAttributeValueException("Object value not provided");
        }

        SNService.ResourceTable type = setResourceType(objectClass);

        if (ObjectClass.ACCOUNT.equals(objectClass) || ObjectClass.GROUP.equals(objectClass)) {
            try {
                client.deleteResource(type, uid.getUidValue());
            } catch (Exception e) {
                SNUtils.wrapGeneralError("Could not delete Resource " + uid.getUidValue(), e);
            }

        } else {
            LOG.warn("Delete of type {0} is not supported", objectClass.getObjectClassValue());
            throw new UnsupportedOperationException("Delete of type"
                    + objectClass.getObjectClassValue() + " is not supported");
        }
    }

    @Override
    public Uid update(ObjectClass objectClass, Uid uid, Set<Attribute> replaceAttributes, OperationOptions options) {
        LOG.ok("Connector UPDATE");

        if (replaceAttributes == null || replaceAttributes.isEmpty()) {
            SNUtils.handleGeneralError("Set of Attributes value is null or empty");
        }

        final AttributesAccessor accessor = new AttributesAccessor(replaceAttributes);

        SNService.ResourceTable type = setResourceType(objectClass);

        if (ObjectClass.ACCOUNT.equals(objectClass) || ObjectClass.GROUP.equals(objectClass)) {
            Uid returnUid = uid;

            Attribute status = accessor.find(OperationalAttributes.ENABLE_NAME);
            String username = accessor.findString(SNAttributes.USER_ATTRIBUTE_USERNAME);
            String name = accessor.findString(SNAttributes.RESOURCE_ATTRIBUTE_NAME);
            if (StringUtil.isBlank(username) && StringUtil.isBlank(name)) {
                name = accessor.findString(Name.NAME);
            }

            Resource resource = new Resource();
            resource.setSysId(uid.getUidValue());
            resource.setUserName(username);
            resource.setUserName(name);

            if (status == null
                    || status.getValue() == null
                    || status.getValue().isEmpty()) {
                LOG.warn("{0} attribute value not correct, can't handle Resource  status update",
                        OperationalAttributes.ENABLE_NAME);
            } else {
                resource.setActive(status.getValue().get(0).toString());
            }

            try {
                resource.fromAttributes(replaceAttributes, configuration.getBaseAddress());

                // password
                GuardedString password = accessor.getPassword() != null
                        ? accessor.getPassword()
                        : accessor.findGuardedString(OperationalAttributes.PASSWORD_NAME);
                if (password == null) {
                    LOG.ok("No password attribute");
                } else {
                    String decryptedPassword = SecurityUtil.decrypt(password);
                    resource.setUserPassword(decryptedPassword);
                }

                client.updateResource(type, resource);

                returnUid = new Uid(resource.getSysId());
            } catch (Exception e) {
                SNUtils.wrapGeneralError(
                        "Could not update Resource " + uid.getUidValue() + " from attributes ", e);
            }

            // also manage memberships
            BatchRequest batchRequest = new BatchRequest(UUID.randomUUID().toString());
            AtomicInteger counter = new AtomicInteger(1);

            // 1. remove current memberships
            client.getMembershipResources(SNService.ResourceTable.sys_user_grmember, "user=" + resource.getSysId())
                    .getResult().forEach(memb -> {
                        try {
                            batchRequest.getRequests()
                                    .add(new BatchOperation.Builder()
                                            .id(String.valueOf(counter.getAndIncrement()))
                                            .url("/api/now/table/" + SNService.ResourceTable.sys_user_grmember.name()
                                                    + "/" + memb.getSysId())
                                            .headers(DEFAULT_HTTP_HEADERS)
                                            .method(HttpMethod.DELETE)
                                            .build());
                        } catch (Exception e) {
                            SNUtils.wrapGeneralError("While deleting old user-group memberships : " + username, e);
                        }
                    });
            // 2. add the new ones
            Optional.ofNullable(AttributeUtil.find(PredefinedAttributes.GROUPS_NAME, replaceAttributes))
                    .ifPresent(groupsAttr -> {
                        groupsAttr.getValue().forEach(group -> {
                            try {
                                batchRequest.getRequests().add(new BatchOperation.Builder()
                                        .id(String.valueOf(counter.getAndIncrement()))
                                        .url("/api/now/table/" + SNService.ResourceTable.sys_user_grmember.name())
                                        .headers(DEFAULT_HTTP_HEADERS)
                                        .method(HttpMethod.POST)
                                        .body(Map.of("user", resource.getSysId(), "group", group.toString()))
                                        .build());
                            } catch (Exception e) {
                                SNUtils.wrapGeneralError(
                                        "While creating user-group memberships on update: " + username, e);
                            }
                        });
                    });

            if (!batchRequest.getRequests().isEmpty()) {
                client.executeBatch(batchRequest);
            }

            return returnUid;

        } else {
            LOG.warn("Update of type {0} is not supported", objectClass.getObjectClassValue());
            throw new UnsupportedOperationException("Update of type"
                    + objectClass.getObjectClassValue() + " is not supported");
        }
    }

    public SNClient getClient() {
        return client;
    }

    private ConnectorObject fromResource(
            final Resource resource,
            final ObjectClass objectClass,
            final Set<String> attributesToGet) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(objectClass);
        builder.setUid(resource.getSysId());
        builder.setName(StringUtil.isBlank(resource.getUserName()) ? resource.getName() : resource.getUserName());

        try {
            Set<Attribute> attributes = resource.toAttributes();
            for (Attribute toAttribute : attributes) {
                String attributeName = toAttribute.getName();
                for (String attributeToGetName : attributesToGet) {
                    if (attributeName.equals(attributeToGetName)) {
                        builder.addAttribute(toAttribute);
                        break;
                    }
                }
            }
        } catch (IllegalArgumentException | IllegalAccessException ex) {
            LOG.error(ex, "While converting to attributes");
        }

        // retrieve also memberships
        if (ObjectClass.ACCOUNT.equals(objectClass) && attributesToGet.contains(PredefinedAttributes.GROUPS_NAME)) {
            builder.addAttribute(AttributeBuilder.build(
                    PredefinedAttributes.GROUPS_NAME,
                    client.getMembershipResources(SNService.ResourceTable.sys_user_grmember,
                            "user=" + resource.getSysId()).getResult().stream()
                            .map(mr -> mr.getGroup().getValue())
                            .collect(Collectors.toList())));
        }

        return builder.build();
    }

    private static SNService.ResourceTable setResourceType(final ObjectClass objectClass) {
        SNService.ResourceTable type = null;
        if (ObjectClass.ACCOUNT.equals(objectClass)) {
            type = SNService.ResourceTable.sys_user;
        } else if (ObjectClass.GROUP.equals(objectClass)) {
            type = SNService.ResourceTable.sys_user_group;
        }
        return type;
    }
}
