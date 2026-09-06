package com.subdual.api_gateway.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HeaderMapRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String> customHeaders = new HashMap<>();

    public HeaderMapRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    public void addHeader(String name, String value) {
        customHeaders.put(name.toLowerCase(), value);
    }

    @Override
    public String getHeader(String name) {
        if (name == null) {
            return null;
        }
        String lower = name.toLowerCase();
        if (customHeaders.containsKey(lower)) {
            return customHeaders.get(lower);
        }
        // Anti-spoofing: Strip client-supplied user identification headers
        if ("x-user-id".equals(lower) || "x-user-email".equals(lower)) {
            return null;
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (name == null) {
            return Collections.emptyEnumeration();
        }
        String lower = name.toLowerCase();
        if (customHeaders.containsKey(lower)) {
            return Collections.enumeration(Collections.singletonList(customHeaders.get(lower)));
        }
        // Anti-spoofing
        if ("x-user-id".equals(lower) || "x-user-email".equals(lower)) {
            return Collections.emptyEnumeration();
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new HashSet<>();
        Enumeration<String> parentNames = super.getHeaderNames();
        while (parentNames != null && parentNames.hasMoreElements()) {
            String name = parentNames.nextElement();
            String lower = name.toLowerCase();
            if (!"x-user-id".equals(lower) && !"x-user-email".equals(lower)) {
                names.add(name);
            }
        }
        names.addAll(customHeaders.keySet());
        return Collections.enumeration(names);
    }
}
