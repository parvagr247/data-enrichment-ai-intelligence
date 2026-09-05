package com.subdual.dataset_service.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity representing a verified fact attribute and its evidence citation.
 */
@Entity
@Table(name = "entity_attributes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrichedAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id", nullable = false)
    @JsonIgnore
    private EnrichedEntity entity;

    @Column(name = "attribute_name", length = 100, nullable = false)
    private String attributeName;

    @Column(name = "attribute_value", columnDefinition = "TEXT", nullable = false)
    private String attributeValue;

    @Column(name = "source_url", length = 1024)
    private String sourceUrl;

    @Column(name = "evidence_snippet", columnDefinition = "TEXT")
    private String evidenceSnippet;

    @Column(name = "confidence", length = 50)
    private String confidence;
}
