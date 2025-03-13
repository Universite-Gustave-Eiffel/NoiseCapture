package org.orbisgis.sos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "bandpass",
    "anti_aliasing",
    "configuration",
    "a_weighting",
    "c_weighting"
})
public class ConfigurationSpectrumChannel {

    public ConfigurationSpectrumChannel() {
    }

    @JsonProperty("bandpass")
    public List<ConfigurationBiquad> bandpass = new ArrayList<>();
    @JsonProperty("anti_aliasing")
    public ConfigurationSos antiAliasing;
    @JsonProperty("configuration")
    public ConfigurationMeta configuration;
    @JsonProperty("a_weighting")
    public ConfigurationDigitalFilter aWeighting;
    @JsonProperty("c_weighting")
    public ConfigurationDigitalFilter cWeighting;

    @JsonProperty("bandpass")
    public List<ConfigurationBiquad> getBandpass() {
        return bandpass;
    }

    @JsonProperty("bandpass")
    public void setBandpass(List<ConfigurationBiquad> bandpass) {
        this.bandpass = bandpass;
    }

    @JsonProperty("anti_aliasing")
    public ConfigurationSos getAntiAliasing() {
        return antiAliasing;
    }

    @JsonProperty("anti_aliasing")
    public void setAntiAliasing(ConfigurationSos antiAliasing) {
        this.antiAliasing = antiAliasing;
    }

    @JsonProperty("configuration")
    public ConfigurationMeta getConfiguration() {
        return configuration;
    }

    @JsonProperty("configuration")
    public void setConfiguration(ConfigurationMeta configuration) {
        this.configuration = configuration;
    }

    @JsonProperty("a_weighting")
    public ConfigurationDigitalFilter getAWeighting() {
        return aWeighting;
    }

    @JsonProperty("a_weighting")
    public void setAWeighting(ConfigurationDigitalFilter aWeighting) {
        this.aWeighting = aWeighting;
    }

    @JsonProperty("c_weighting")
    public ConfigurationDigitalFilter getCWeighting() {
        return cWeighting;
    }

    @JsonProperty("c_weighting")
    public void setCWeighting(ConfigurationDigitalFilter cWeighting) {
        this.cWeighting = cWeighting;
    }
}
