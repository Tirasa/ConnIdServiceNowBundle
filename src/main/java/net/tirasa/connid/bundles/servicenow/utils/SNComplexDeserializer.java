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
package net.tirasa.connid.bundles.servicenow.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import net.tirasa.connid.bundles.servicenow.dto.BaseEntity;
import net.tirasa.connid.bundles.servicenow.dto.SNComplex;
import org.identityconnectors.common.logging.Log;

public class SNComplexDeserializer extends JsonDeserializer<SNComplex> {

    private static final Log LOG = Log.getLog(BaseEntity.class);

    @Override
    public SNComplex deserialize(final JsonParser p, final DeserializationContext ctxt) throws IOException {
        SNComplex ldapServer = null;

        JsonToken currentToken = p.getCurrentToken();

        if (currentToken == JsonToken.VALUE_STRING) {
            LOG.ok("Complex object is represented as a string value {0}. Returning null.", p.getText());
        } else if (currentToken == JsonToken.START_OBJECT) {
            JsonNode node = p.getCodec().readTree(p);
            ldapServer = new SNComplex();
            ldapServer.setLink(node.get("link").asText());
            ldapServer.setValue(node.get("value").asText());
        }

        return ldapServer;
    }
}
