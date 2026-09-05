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

import java.time.Instant;

/**
 * JPA entity representing a discovered web source tied to an enriched entity.
 */
@Entity
@Table(name = "entity_sources")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrichedSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id", nullable = false)
    @JsonIgnore
    private EnrichedEntity entity;

    @Column(name = "source_url", length = 1024, nullable = false)
    private String sourceUrl;

    @Column(name = "title", length = 512)
    private String title;

    @Column(name = "snippet", columnDefinition = "TEXT")
    private String snippet;

    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Column(name = "domain", length = 255)
    private String domain;

    @Column(name = "provider", length = 50)
    private String provider;

    @Column(name = "relevance")
    private Double relevance;

    @Column(name = "retrieved_at")
    private Instant retrievedAt;
}
