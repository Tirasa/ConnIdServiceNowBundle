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
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import net.tirasa.connid.bundles.servicenow.utils.SNUtils;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

public class BatchOperation implements Serializable {

    private static final long serialVersionUID = -3701841336691292180L;

    @JsonProperty("id")
    private String id;

    @JsonProperty("method")
    private String method;

    @JsonProperty("url")
    private String url;

    @JsonProperty("headers")
    private List<Map<String, String>> headers;

    @JsonProperty("body")
    private String body;

    public static class Builder {

        private BatchOperation instance;

        private final BatchOperation getInstance() {
            if (instance == null) {
                instance = new BatchOperation();
            }
            return instance;
        }

        public Builder id(final String id) {
            getInstance().setId(id);
            return this;
        }

        public Builder method(final String method) {
            getInstance().setMethod(method);
            return this;
        }

        public Builder url(final String relativeUrl) {
            getInstance().setUrl(relativeUrl);
            return this;
        }

        public Builder headers(final List<Map<String, String>> headers) {
            getInstance().setHeaders(headers);
            return this;
        }

        public Builder body(final Object body) {
            getInstance().setBody(body);
            return this;
        }

        public BatchOperation build() {
            return getInstance();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(final String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public List<Map<String, String>> getHeaders() {
        return headers;
    }

    public void setHeaders(final List<Map<String, String>> headers) {
        this.headers = headers;
    }

    public String getBody() {
        return body;
    }

    public void setBody(final String body) {
        this.body = body;
    }

    public void setBody(final Object body) {
        try {
            this.body = Base64.getEncoder().encodeToString(
                    SNUtils.MAPPER.writeValueAsString(body).getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new ConnectorException(e);
        }
    }

    @Override
    public String toString() {
        return "BatchOperation{"
                + "method='" + method + '\''
                + ", relativeUrl='" + url
                + '\'' + ", headers=" + headers
                + ", body=" + body
                + '}';
    }
}
