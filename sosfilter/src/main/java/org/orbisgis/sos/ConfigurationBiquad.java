package org.orbisgis.sos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "sos",
    "center_frequency",
    "max_frequency",
    "min_frequency",
    "nominal_frequency",
    "subsampling_depth",
    "subsampling_filter"
})
public class ConfigurationBiquad {

    public ConfigurationBiquad() {
    }

    @JsonProperty("sos")
    private ConfigurationSos sos;
    @JsonProperty("center_frequency")
    private Double centerFrequency;
    @JsonProperty("max_frequency")
    private Double maxFrequency;
    @JsonProperty("min_frequency")
    private Double minFrequency;
    @JsonProperty("nominal_frequency")
    private Double nominalFrequency;
    @JsonProperty("subsampling_depth")
    private int subsamplingDepth;
    @JsonProperty("subsampling_filter")
    private ConfigurationBiquad subsamplingFilter;

    @JsonProperty("sos")
    public ConfigurationSos getSos() {
        return sos;
    }

    @JsonProperty("sos")
    public void setSos(ConfigurationSos sos) {
        this.sos = sos;
    }

    @JsonProperty("center_frequency")
    public Double getCenterFrequency() {
        return centerFrequency;
    }

    @JsonProperty("center_frequency")
    public void setCenterFrequency(Double centerFrequency) {
        this.centerFrequency = centerFrequency;
    }

    @JsonProperty("max_frequency")
    public Double getMaxFrequency() {
        return maxFrequency;
    }

    @JsonProperty("max_frequency")
    public void setMaxFrequency(Double maxFrequency) {
        this.maxFrequency = maxFrequency;
    }

    @JsonProperty("min_frequency")
    public Double getMinFrequency() {
        return minFrequency;
    }

    @JsonProperty("min_frequency")
    public void setMinFrequency(Double minFrequency) {
        this.minFrequency = minFrequency;
    }

    @JsonProperty("nominal_frequency")
    public Double getNominalFrequency() {
        return nominalFrequency;
    }

    @JsonProperty("nominal_frequency")
    public void setNominalFrequency(Double nominalFrequency) {
        this.nominalFrequency = nominalFrequency;
    }

    @JsonProperty("subsampling_depth")
    public int getSubsamplingDepth() {
        return subsamplingDepth;
    }

    @JsonProperty("subsampling_depth")
    public void setSubsamplingDepth(int subsamplingDepth) {
        this.subsamplingDepth = subsamplingDepth;
    }

    @JsonProperty("subsampling_filter")
    public ConfigurationBiquad getSubsamplingFilter() {
        return subsamplingFilter;
    }

    @JsonProperty("subsampling_filter")
    public void setSubsamplingFilter(ConfigurationBiquad subsamplingFilter) {
        this.subsamplingFilter = subsamplingFilter;
    }

}
