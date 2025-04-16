package org.orbisgis.sos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "b0",
    "b1",
    "b2",
    "a1",
    "a2",
    "sample_ratio"
})
public class ConfigurationSos {

    @JsonProperty("b0")
    private List<Double> b0 = new ArrayList<Double>();
    @JsonProperty("b1")
    private List<Double> b1 = new ArrayList<Double>();
    @JsonProperty("b2")
    private List<Double> b2 = new ArrayList<Double>();
    @JsonProperty("a1")
    private List<Double> a1 = new ArrayList<Double>();
    @JsonProperty("a2")
    private List<Double> a2 = new ArrayList<Double>();
    @JsonProperty("sample_ratio")
    private int sampleRatio=1;


    @JsonProperty("b0")
    public List<Double> getB0() {
        return b0;
    }

    @JsonProperty("b0")
    public void setB0(List<Double> b0) {
        this.b0 = b0;
    }

    @JsonProperty("b1")
    public List<Double> getB1() {
        return b1;
    }

    @JsonProperty("b1")
    public void setB1(List<Double> b1) {
        this.b1 = b1;
    }

    @JsonProperty("b2")
    public List<Double> getB2() {
        return b2;
    }

    @JsonProperty("b2")
    public void setB2(List<Double> b2) {
        this.b2 = b2;
    }

    @JsonProperty("a1")
    public List<Double> getA1() {
        return a1;
    }

    @JsonProperty("a1")
    public void setA1(List<Double> a1) {
        this.a1 = a1;
    }

    @JsonProperty("a2")
    public List<Double> getA2() {
        return a2;
    }

    @JsonProperty("a2")
    public void setA2(List<Double> a2) {
        this.a2 = a2;
    }

    @JsonProperty("sample_ratio")
    public int getSampleRatio() {
        return sampleRatio;
    }

    @JsonProperty("sample_ratio")
    public void setSampleRatio(int sampleRatio) {
        this.sampleRatio = sampleRatio;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigurationSos that = (ConfigurationSos) o;
        return sampleRatio == that.sampleRatio && Objects.equals(b0, that.b0) &&
                Objects.equals(b1, that.b1) && Objects.equals(b2, that.b2) &&
                Objects.equals(a1, that.a1) && Objects.equals(a2, that.a2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(b0, b1, b2, a1, a2, sampleRatio);
    }
}
