package com.subdual.research_service.api.validation;

import com.subdual.research_service.api.dto.request.ResearchRequest;
import com.subdual.research_service.common.exception.BusinessRuleException;
import com.subdual.research_service.util.UrlNormalizer;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class ResearchRequestValidator {

    public void validate(ResearchRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Request body must not be null");
        }

        if (request.url() != null && !isValidUrl(request.url())) {
            throw new BusinessRuleException("Field 'url' must be a valid, well-formed HTTP/HTTPS URL");
        }

        boolean hasUrl = request.url() != null && !request.url().isBlank();
        boolean hasName = request.name() != null && !request.name().isBlank();

        if (!hasUrl && !hasName) {
            throw new BusinessRuleException("Either 'url' or 'name' must be provided for research");
        }
    }

    public boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String cleanUrl = UrlNormalizer.unwrapLink(url);
        if (cleanUrl.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(cleanUrl.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                return false;
            }
            return uri.getHost() != null && !uri.getHost().isBlank();
        } catch (Exception ex) {
            return false;
        }
    }
}
