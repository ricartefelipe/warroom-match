package br.com.ricarte.warroom.support;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;


/** Small helper over MockMvc: real login with magic link, role choice and JSON calls. */
public final class ApiClient {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public ApiClient(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    public MockMvc mockMvc() {
        return mockMvc;
    }

    public String login(String email) throws Exception {
        JsonNode magic = json(MockMvcRequestBuilders.post("/v1/auth/magic-link"), null,
                "{\"email\":\"" + email + "\",\"name\":\"" + email.split("@")[0] + "\"}");
        String link = magic.get("magicLink").asText();
        String token = link.substring(link.indexOf("token=") + 6);
        JsonNode session = json(MockMvcRequestBuilders.post("/v1/auth/verify"), null, "{\"token\":\"" + token + "\"}");
        return session.get("sessionToken").asText();
    }

    public String signup(String role) throws Exception {
        String token = login(role + "-" + UUID.randomUUID() + "@example.com");
        json(MockMvcRequestBuilders.post("/v1/me/role"), token, "{\"role\":\"" + role + "\"}");
        return token;
    }

    public JsonNode json(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        MvcResult result = perform(request, token, body);
        int status = result.getResponse().getStatus();
        if (status >= 400) {
            throw new AssertionError("HTTP " + status + ": " + result.getResponse().getContentAsString());
        }
        String content = result.getResponse().getContentAsString();
        return content.isBlank() ? objectMapper.nullNode() : objectMapper.readTree(content);
    }

    public int status(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        return perform(request, token, body).getResponse().getStatus();
    }

    public MvcResult perform(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request).andReturn();
    }

    public JsonNode get(String path, String token) throws Exception {
        return json(MockMvcRequestBuilders.get(path), token, null);
    }

    public JsonNode post(String path, String token, String body) throws Exception {
        return json(MockMvcRequestBuilders.post(path), token, body);
    }

    public JsonNode put(String path, String token, String body) throws Exception {
        return json(MockMvcRequestBuilders.put(path), token, body);
    }
}
