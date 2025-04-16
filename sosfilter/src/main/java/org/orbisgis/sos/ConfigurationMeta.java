package org.orbisgis.sos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "sample_rate"
})
public class ConfigurationMeta {

    public ConfigurationMeta() {
    }

    @JsonProperty("sample_rate")
    private Integer sampleRate;

    @JsonProperty("sample_rate")
    public Integer getSampleRate() {
        return sampleRate;
    }

    @JsonProperty("sample_rate")
    public void setSampleRate(Integer sampleRate) {
        this.sampleRate = sampleRate;
    }

}
