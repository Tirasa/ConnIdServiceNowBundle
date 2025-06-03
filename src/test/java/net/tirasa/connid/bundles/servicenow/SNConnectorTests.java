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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import net.tirasa.connid.bundles.servicenow.dto.PagedResults;
import net.tirasa.connid.bundles.servicenow.dto.Resource;
import net.tirasa.connid.bundles.servicenow.dto.SNComplex;
import net.tirasa.connid.bundles.servicenow.service.NoSuchEntityException;
import net.tirasa.connid.bundles.servicenow.service.SNClient;
import net.tirasa.connid.bundles.servicenow.service.SNService;
import net.tirasa.connid.bundles.servicenow.utils.SNAttributes;
import net.tirasa.connid.bundles.servicenow.utils.SNUtils;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.PredefinedAttributes;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.SortKey;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.test.common.TestHelpers;
import org.identityconnectors.test.common.ToListResultsHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SNConnectorTests {

    private static final Log LOG = Log.getLog(SNConnectorTests.class);

    private static final String USERNAME = "testuser_";

    private static final String GROUPNAME = "testgrp_";

    private static final String PASSWORD = "Password01";

    private static final String PASSWORD_UPDATE = "Password0100";

    private static final String VALUE_EMPLOYEE_NUMBER = "Employee number";

    private static final String VALUE_EMPLOYEE_NUMBER_UPDATE = "Updated employee number";

    private static final String VALUE_CITY = "Rome";

    private static final String VALUE_COMMENT = "My comment";

    private static final String VALUE_CITY_UPDATE = "Milan";

    private static final String VALUE_LOCATION = "location value";

    private static final String VALUE_MANAGER = "manager value";

    private static final String RESOURCE_ATTRIBUTE_EMPLOYEE_NUMBER = "employee_number";

    private static final String RESOURCE_ATTRIBUTE_LOCATION = "location";

    private static final String RESOURCE_ATTRIBUTE_MANAGER = "manager";

    private static final String RESOURCE_ATTRIBUTE_CITY = "city";

    private static final Properties PROPS = new Properties();

    private static SNConnectorConfiguration CONF;

    private static SNConnector CONN;

    private static ConnectorFacade connector;

    private static SNConnectorConfiguration buildConfiguration(Map<String, String> configuration) {
        SNConnectorConfiguration connectorConfiguration = new SNConnectorConfiguration();

        for (Map.Entry<String, String> entry : configuration.entrySet()) {

            switch (entry.getKey()) {
                case "auth.baseAddress":
                    connectorConfiguration.setBaseAddress(entry.getValue());
                    break;
                case "auth.password":
                    connectorConfiguration.setPassword(SNUtils.createProtectedPassword(entry.getValue()));
                    break;
                case "auth.username":
                    connectorConfiguration.setUsername(entry.getValue());
                    break;
                default:
                    LOG.warn("Occurrence of undefined parameter");
                    break;
            }
        }
        return connectorConfiguration;
    }

    private static boolean isConfigurationValid(final SNConnectorConfiguration connectorConfiguration) {
        connectorConfiguration.validate();
        return true;
    }

    @BeforeAll
    public static void setUpConf() throws IOException {
        PROPS.load(SNConnectorTests.class.getResourceAsStream("auth.properties"));

        Map<String, String> configurationParameters = new HashMap<>();
        for (final String name : PROPS.stringPropertyNames()) {
            configurationParameters.put(name, PROPS.getProperty(name));
        }
        CONF = buildConfiguration(configurationParameters);

        Boolean isValid = isConfigurationValid(CONF);
        if (isValid) {
            CONN = new SNConnector();
            CONN.init(CONF);
            try {
                CONN.test();
            } catch (Exception e) {
                LOG.error(e, "While testing connector");
            }
            CONN.schema();
        }

        connector = newFacade();

        assertNotNull(CONF);
        assertNotNull(isValid);
        assertNotNull(CONF.getBaseAddress());
        assertNotNull(CONF.getPassword());
        assertNotNull(CONF.getUsername());
    }

    private static ConnectorFacade newFacade() {
        ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
        APIConfiguration impl = TestHelpers.createTestConfiguration(SNConnector.class, CONF);
        impl.getResultsHandlerConfiguration().setFilteredResultsHandlerInValidationMode(true);
        return factory.newInstance(impl);
    }

    private static SNClient newClient() {
        return CONN.getClient();
    }

    @Test
    public void validate() {
        newFacade().validate();
    }

    @Test
    public void schema() {
        Schema schema = newFacade().schema();
        assertEquals(2, schema.getObjectClassInfo().size());

        boolean accountFound = false;
        boolean groupFound = false;
        for (ObjectClassInfo oci : schema.getObjectClassInfo()) {
            if (ObjectClass.ACCOUNT_NAME.equals(oci.getType())) {
                accountFound = true;
            } else if (ObjectClass.GROUP_NAME.equals(oci.getType())) {
                groupFound = true;
            }
        }
        assertTrue(accountFound);
        assertTrue(groupFound);
    }

    @Test
    public void search() {
        ToListResultsHandler handler = new ToListResultsHandler();

        SearchResult result = connector.search(ObjectClass.ACCOUNT,
                null,
                handler,
                new OperationOptionsBuilder().build());
        assertNotNull(result);
        assertNull(result.getPagedResultsCookie());
        assertEquals(-1, result.getRemainingPagedResults());
        assertFalse(handler.getObjects().isEmpty());

        result = connector.search(ObjectClass.ACCOUNT,
                null,
                handler,
                new OperationOptionsBuilder().setPageSize(1).build());
        assertNotNull(result);
        assertNotNull(result.getPagedResultsCookie());
        assertEquals(-1, result.getRemainingPagedResults());

        result = connector.search(ObjectClass.ACCOUNT,
                null,
                handler,
                new OperationOptionsBuilder().setPagedResultsOffset(2).setPageSize(1).build());
        assertNotNull(result);
        assertNotNull(result.getPagedResultsCookie());
        assertEquals(-1, result.getRemainingPagedResults());
    }

    private static void cleanup(
            final ConnectorFacade connector,
            final SNClient client,
            final String testUserUid,
            final String testGroupUid) {

        if (testUserUid != null) {
            connector.delete(ObjectClass.ACCOUNT, new Uid(testUserUid), new OperationOptionsBuilder().build());
            try {
                client.deleteResource(SNService.ResourceTable.sys_user, testUserUid);
                fail(); // must fail
            } catch (ConnectorException e) {
                assertNotNull(e);
            }

            try {
                client.getResource(SNService.ResourceTable.sys_user, testUserUid);
                fail(); // must fail
            } catch (NoSuchEntityException e) {
                assertNotNull(e);
            }
        }

        if (testGroupUid != null) {
            connector.delete(ObjectClass.ACCOUNT, new Uid(testGroupUid), new OperationOptionsBuilder().build());
            try {
                client.deleteResource(SNService.ResourceTable.sys_user_group, testGroupUid);
                fail(); // must fail
            } catch (ConnectorException e) {
                assertNotNull(e);
            }

            try {
                client.getResource(SNService.ResourceTable.sys_user_group, testGroupUid);
                fail(); // must fail
            } catch (NoSuchEntityException e) {
                assertNotNull(e);
            }
        }
    }

    private void cleanup(
            final SNClient client,
            final String testUserUid,
            final String testGroupUid) {
        if (testUserUid != null) {
            client.deleteResource(SNService.ResourceTable.sys_user, testUserUid);

            try {
                client.getResource(SNService.ResourceTable.sys_user, testUserUid);
                fail(); // must fail
            } catch (ConnectorException e) {
                assertNotNull(e);
            }
        }

        if (testGroupUid != null) {
            client.deleteResource(SNService.ResourceTable.sys_user_group, testUserUid);

            try {
                client.getResource(SNService.ResourceTable.sys_user_group, testUserUid);
                fail(); // must fail
            } catch (ConnectorException e) {
                assertNotNull(e);
            }
        }
    }

    @Test
    public void crud() {
        ConnectorFacade connector = newFacade();
        SNClient client = newClient();

        String testUser = null;
        String testGroup = null;
        UUID uidGrp01 = UUID.randomUUID();
        UUID uidGrp02 = UUID.randomUUID();
        UUID uidGrp03 = UUID.randomUUID();
        UUID uid = UUID.randomUUID();

        try {
            // create group
            Uid createdGroup01 = createGroup(uidGrp01);
            Uid createdGroup02 = createGroup(uidGrp02);
            Uid createdGroup03 = createGroup(uidGrp03);

            Uid created = createUser(uid, createdGroup01.getUidValue());
            testUser = created.getUidValue();

            Resource createdUser = readUser(testUser, client);
            assertEquals(createdUser.getSysId(), created.getUidValue());

            // check memberships
            List<ConnectorObject> found = new ArrayList<>();
            connector.search(ObjectClass.ACCOUNT,
                    new EqualsFilter(new Uid(createdUser.getSysId())),
                    found::add, new OperationOptionsBuilder().setAttributesToGet(
                            SNAttributes.USER_ATTRIBUTE_USERNAME,
                            PredefinedAttributes.GROUPS_NAME).build());
            assertEquals(found.size(), 1);
            assertNotNull(found.get(0));
            assertNotNull(found.get(0).getName());
            assertNotNull(found.get(0).getAttributeByName(PredefinedAttributes.GROUPS_NAME));
            assertNotNull(found.get(0).getAttributeByName(PredefinedAttributes.GROUPS_NAME).getValue());
            assertFalse(found.get(0).getAttributeByName(PredefinedAttributes.GROUPS_NAME).getValue().isEmpty());
            assertTrue(found.get(0).getAttributeByName(PredefinedAttributes.GROUPS_NAME).getValue()
                    .contains(createdGroup01.getUidValue()));

            Uid updated = updateUser(created, createdGroup02.getUidValue(), createdGroup03.getUidValue());

            Resource updatedUser = readUser(updated.getUidValue(), client);
            LOG.info("Updated User: {0}", updatedUser);
            assertNotNull(updatedUser.getUserPassword()); // password returned in clear text
            assertEquals(updatedUser.getEmployeeNumber(), VALUE_EMPLOYEE_NUMBER_UPDATE);
            assertFalse(StringUtil.isBlank(updatedUser.getUserPassword()));
            assertTrue(StringUtil.isBlank(updatedUser.getCity()));
            assertEquals(updatedUser.getUserPassword(), PASSWORD_UPDATE);

            found.clear();
            connector.search(ObjectClass.ACCOUNT,
                    new EqualsFilter(new Uid(createdUser.getSysId())),
                    found::add, new OperationOptionsBuilder().setAttributesToGet(SNAttributes.USER_ATTRIBUTE_USERNAME,
                            PredefinedAttributes.GROUPS_NAME).build());
            assertEquals(found.size(), 1);
            assertNotNull(found.get(0));
            assertNotNull(found.get(0).getName());
            assertNotNull(found.get(0).getAttributeByName(PredefinedAttributes.GROUPS_NAME));
            assertNotNull(found.get(0).getAttributeByName(PredefinedAttributes.GROUPS_NAME).getValue());
            assertFalse(found.get(0).getAttributeByName(PredefinedAttributes.GROUPS_NAME).getValue().isEmpty());
            assertFalse(found.get(0).getAttributeByName(PredefinedAttributes.GROUPS_NAME).getValue()
                    .contains(createdGroup01.getUidValue()));
            assertTrue(found.get(0).getAttributeByName(PredefinedAttributes.GROUPS_NAME).getValue()
                    .contains(createdGroup02.getUidValue()));
            assertTrue(found.get(0).getAttributeByName(PredefinedAttributes.GROUPS_NAME).getValue()
                    .contains(createdGroup03.getUidValue()));
        } catch (Exception e) {
            LOG.error(e, "While running test");
            fail(e.getMessage());
        } finally {
            cleanup(connector, client, testUser, testGroup);
        }
    }

    private static Uid createUser(final UUID uid, final String... grpUids) {
        Attribute password = AttributeBuilder.buildPassword(new GuardedString(PASSWORD.toCharArray()));

        Set<Attribute> userAttrs = new HashSet<>();
        userAttrs.add(AttributeBuilder.build(SNAttributes.USER_ATTRIBUTE_USERNAME, USERNAME + uid));
        userAttrs.add(AttributeBuilder.build(RESOURCE_ATTRIBUTE_EMPLOYEE_NUMBER, VALUE_EMPLOYEE_NUMBER));
        userAttrs.add(AttributeBuilder.build(RESOURCE_ATTRIBUTE_LOCATION, VALUE_LOCATION));
        userAttrs.add(AttributeBuilder.build(RESOURCE_ATTRIBUTE_MANAGER, VALUE_MANAGER));
        userAttrs.add(AttributeBuilder.build(RESOURCE_ATTRIBUTE_CITY, VALUE_CITY));
        userAttrs.add(AttributeBuilder.build(SNAttributes.RESOURCE_ATTRIBUTE_LDAP_SERVER, "dev LDAP"));
        userAttrs.add(AttributeBuilder.build(PredefinedAttributes.GROUPS_NAME, Arrays.asList(grpUids)));
        userAttrs.add(password);

        Uid created = connector.create(ObjectClass.ACCOUNT, userAttrs, new OperationOptionsBuilder().build());
        assertNotNull(created);
        assertFalse(created.getUidValue().isEmpty());
        LOG.info("Created User uid: {0}", created);

        return created;
    }

    private static Uid createGroup(final UUID uid) {
        Set<Attribute> grpAttrs = new HashSet<>();
        grpAttrs.add(AttributeBuilder.build(SNAttributes.RESOURCE_ATTRIBUTE_NAME, GROUPNAME + uid.toString()));

        Uid created = connector.create(ObjectClass.GROUP, grpAttrs, new OperationOptionsBuilder().build());
        assertNotNull(created);
        assertFalse(created.getUidValue().isEmpty());
        LOG.info("Created Group uid: {0}", created);

        return created;
    }

    private static Uid updateUser(final Uid created, final String... grpUids) {
        Attribute password = AttributeBuilder.buildPassword(new GuardedString((PASSWORD_UPDATE).toCharArray()));
        // UPDATE USER PASSWORD
        Set<Attribute> userAttrs = new HashSet<>();
        userAttrs.add(password);

        // want to update another attribute
        userAttrs.add(AttributeBuilder.build(RESOURCE_ATTRIBUTE_EMPLOYEE_NUMBER, VALUE_EMPLOYEE_NUMBER_UPDATE));

        // want to clear an attribute
        userAttrs.add(AttributeBuilder.build(RESOURCE_ATTRIBUTE_CITY, ""));
        // groups to replace
        userAttrs.add(AttributeBuilder.build(PredefinedAttributes.GROUPS_NAME, Arrays.asList(grpUids)));

        Uid updated = connector.update(
                ObjectClass.ACCOUNT, created, userAttrs, new OperationOptionsBuilder().build());
        assertNotNull(updated);
        assertFalse(updated.getUidValue().isEmpty());
        LOG.info("Updated User uid: {0}", updated);

        return updated;
    }

    private static boolean hasAttribute(final Set<Attribute> attrs, final String name) {
        return AttributeUtil.find(name, attrs) != null;
    }

    private static Resource readUser(final String id, final SNClient client)
            throws IllegalArgumentException, IllegalAccessException {
        Resource user = client.getResource(SNService.ResourceTable.sys_user, id);
        assertNotNull(user);
        assertNotNull(user.getSysId());
        assertEquals(user.getLocation().getValue(), VALUE_LOCATION);
        assertEquals(user.getManager().getValue(), VALUE_MANAGER);
        assertNotNull(user.getEmployeeNumber());
        assertNotNull(user.getLdapServer());
        assertEquals(CONF.getBaseAddress() + "api/now/table/ldap_server_config/dev LDAP",
                user.getLdapServer().getLink());
        assertEquals("dev LDAP", user.getLdapServer().getValue());
        assertTrue(StringUtil.isBlank(user.getEmail()));
        LOG.info("Found User: {0}", user);

        // USER TO ATTRIBUTES
        Set<Attribute> toAttributes = user.toAttributes();
        LOG.info("User to attributes: {0}", toAttributes);
        assertTrue(hasAttribute(toAttributes, SNAttributes.RESOURCE_ATTRIBUTE_ID));
        assertTrue(hasAttribute(toAttributes, SNAttributes.USER_ATTRIBUTE_USERNAME));
        assertTrue(hasAttribute(toAttributes, SNAttributes.RESOURCE_ATTRIBUTE_NAME));
        assertTrue(hasAttribute(toAttributes, RESOURCE_ATTRIBUTE_EMPLOYEE_NUMBER));
        assertTrue(hasAttribute(toAttributes, RESOURCE_ATTRIBUTE_LOCATION));
        assertTrue(hasAttribute(toAttributes, RESOURCE_ATTRIBUTE_MANAGER));
        assertTrue(hasAttribute(toAttributes, RESOURCE_ATTRIBUTE_CITY));

        // SEARCH BY USERNAME
        final List<ConnectorObject> found = new ArrayList<>();
        connector.search(ObjectClass.ACCOUNT,
                new EqualsFilter(new Name(user.getUserName())),
                found::add,
                new OperationOptionsBuilder().setAttributesToGet(SNAttributes.USER_ATTRIBUTE_USERNAME).build());
        assertEquals(found.size(), 1);
        assertNotNull(found.get(0));
        assertNotNull(found.get(0).getName());
        LOG.info("Found User using Connector search: {0}", found.get(0));

        return user;
    }

    @Test
    public void pagedSearch() {
        final List<ConnectorObject> results = new ArrayList<>();
        final ResultsHandler handler = results::add;

        final OperationOptionsBuilder oob = new OperationOptionsBuilder();
        oob.setAttributesToGet(SNAttributes.USER_ATTRIBUTE_USERNAME);
        oob.setPageSize(1);
        oob.setSortKeys(new SortKey(SNAttributes.USER_ATTRIBUTE_USERNAME, false));

        connector.search(ObjectClass.ACCOUNT, null, handler, oob.build());

        assertEquals(1, results.size());

        results.clear();

        int totalCount = newClient().getResources(SNService.ResourceTable.sys_user).getTotalCount();
        oob.setPageSize(totalCount > 3 ? totalCount / 3 : totalCount);
        String cookie = "";
        do {
            oob.setPagedResultsCookie(cookie);
            final SearchResult searchResult = connector.search(ObjectClass.ACCOUNT, null, handler, oob.build());
            cookie = searchResult.getPagedResultsCookie();
        } while (cookie != null);
        LOG.info("Paged search results : {0}", results);

        assertTrue(results.size() > 2);
    }

    private static Resource createUserServiceTest(final UUID uid, final SNClient client) {
        Resource user = new Resource();
        user.setUserName(USERNAME + uid.toString());
        user.setUserPassword(PASSWORD);
        user.setActive("true");
        user.setCity(VALUE_CITY);
        user.setComments(VALUE_COMMENT);
        SNComplex ldapServer = new SNComplex("dev LDAP");
        ldapServer.setLink(CONF.getBaseAddress() + "api/now/table/ldap_server_config/dev LDAP");
        user.setLdapServer(ldapServer);

        Resource created = client.createResource(SNService.ResourceTable.sys_user, user);
        assertNotNull(created);
        assertNotNull(created.getSysId());
        LOG.info("Created User: {0}", created);

        return created;
    }

    private static Resource updateUserServiceTest(final String userId, final SNClient client) {
        Resource user = client.getResource(SNService.ResourceTable.sys_user, userId);
        assertNotNull(user);
        assertFalse(user.getCity().isEmpty());

        String oldCity = user.getCity();

        // want to update an attribute
        user.setCity(VALUE_CITY_UPDATE);

        // want also to reset an attribute
        user.setComments(null);

        Resource updated = client.updateResource(SNService.ResourceTable.sys_user, user);
        assertNotNull(updated);
        assertFalse(updated.getCity().equals(oldCity));
        assertEquals(updated.getCity(), VALUE_CITY_UPDATE);
        assertNull(updated.getComments());
        LOG.info("Updated User: {0}", updated);

        // test removed attribute
        return updated;
    }

    private static void readUsersServiceTest(final SNClient client) {
        // GET USERS
        PagedResults<Resource> paged = client.getResources(SNService.ResourceTable.sys_user, 0, 1, false);
        assertNotNull(paged);
        assertFalse(paged.getResult().isEmpty());
        assertTrue(paged.getResult().size() == 1);
        assertNotEquals(paged.getTotalCount(), 1);
        LOG.info("Paged Users: {0}", paged);

        PagedResults<Resource> paged2 = client.getResources(SNService.ResourceTable.sys_user, 1, 1, false);
        assertNotNull(paged2);
        assertFalse(paged2.getResult().isEmpty());
        assertTrue(paged2.getResult().size() == 1);
        LOG.info("Paged Users next page: {0}", paged2);
    }

    private Resource readUserServiceTest(final String id, final SNClient client)
            throws IllegalArgumentException, IllegalAccessException {

        // GET USER
        Resource user = client.getResource(SNService.ResourceTable.sys_user, id);
        assertNotNull(user);
        assertNotNull(user.getSysId());
        assertNotNull(user.getLdapServer());
        assertEquals(CONF.getBaseAddress() + "api/now/table/ldap_server_config/dev LDAP",
                user.getLdapServer().getLink());
        assertEquals("dev LDAP", user.getLdapServer().getValue());
        LOG.info("Found User: {0}", user);

        // USER TO ATTRIBUTES
        Set<Attribute> toAttributes = user.toAttributes();
        LOG.info("User to attributes: {0}", toAttributes);
        assertTrue(hasAttribute(toAttributes, SNAttributes.RESOURCE_ATTRIBUTE_ID));
        assertTrue(hasAttribute(toAttributes, SNAttributes.USER_ATTRIBUTE_USERNAME));
        assertTrue(hasAttribute(toAttributes, SNAttributes.RESOURCE_ATTRIBUTE_NAME));
        assertTrue(hasAttribute(toAttributes, SNAttributes.RESOURCE_ATTRIBUTE_LDAP_SERVER));

        // GET USER by userName
        List<Resource> users = client.getResources(SNService.ResourceTable.sys_user,
                SNAttributes.USER_ATTRIBUTE_USERNAME + "=" + user.getUserName()).getResult();
        assertNotNull(users);
        assertFalse(users.isEmpty());
        assertNotNull(users.get(0).getSysId());
        LOG.info("Found User by {0}: {1}", SNAttributes.USER_ATTRIBUTE_USERNAME, users.get(0));

        return user;
    }

    private static void deleteUsersServiceTest(final SNClient client) {
        PagedResults<Resource> users = client.getResources(SNService.ResourceTable.sys_user,
                SNAttributes.USER_ATTRIBUTE_USERNAME + "STARTSWITH" + USERNAME, 0, 100, false);
        assertNotNull(users);
        if (!users.getResult().isEmpty()) {
            for (Resource user : users.getResult()) {
                client.deleteResource(SNService.ResourceTable.sys_user, user.getSysId());
            }
        }
    }

    private static void readGroupsServiceTest(final SNClient client) {
        // GET GROUPS
        PagedResults<Resource> paged = client.getResources(SNService.ResourceTable.sys_user_group, 0, 1, false);
        assertNotNull(paged);
        assertFalse(paged.getResult().isEmpty());
        assertTrue(paged.getResult().size() == 1);
        assertNotEquals(paged.getTotalCount(), 1);
        LOG.info("Paged Groups: {0}", paged);

        PagedResults<Resource> paged2 = client.getResources(SNService.ResourceTable.sys_user_group, 1, 1, false);
        assertNotNull(paged2);
        assertFalse(paged2.getResult().isEmpty());
        assertTrue(paged2.getResult().size() == 1);
        LOG.info("Paged Groups next page: {0}", paged2);
    }

    @Test
    public void serviceTest() {
        SNClient client = newClient();

        String testUser = null;
        String testGroup = null;
        UUID uid = UUID.randomUUID();
        try {
            deleteUsersServiceTest(client);

            Resource created = createUserServiceTest(uid, client);
            testUser = created.getSysId();

            readUserServiceTest(testUser, client);

            readUsersServiceTest(client);
            readGroupsServiceTest(client);

            updateUserServiceTest(testUser, client);
        } catch (Exception e) {
            LOG.error(e, "While running test");
            fail(e.getMessage());
        } finally {
            cleanup(client, testUser, testGroup);
        }
    }
}
