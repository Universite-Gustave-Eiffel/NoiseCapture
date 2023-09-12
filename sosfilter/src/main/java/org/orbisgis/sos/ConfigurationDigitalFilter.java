package org.orbisgis.sos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "filter_denominator",
    "filter_numerator",
    "sample_ratio"
})
public class ConfigurationDigitalFilter {

    @JsonProperty("filter_denominator")
    private List<Double> filterDenominator = new ArrayList<Double>();

    @JsonProperty("filter_numerator")
    private List<Double> filterNumerator = new ArrayList<Double>();

    @JsonProperty("filter_denominator")
    public List<Double> getFilterDenominator() {
        return filterDenominator;
    }

    @JsonProperty("filter_denominator")
    public void setFilterDenominator(List<Double> filterDenominator) {
        this.filterDenominator = filterDenominator;
    }

    @JsonProperty("filter_numerator")
    public List<Double> getFilterNumerator() {
        return filterNumerator;
    }

    @JsonProperty("filter_numerator")
    public void setFilterNumerator(List<Double> filterNumerator) {
        this.filterNumerator = filterNumerator;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(ConfigurationDigitalFilter.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("filterDenominator");
        sb.append('=');
        sb.append(((this.filterDenominator == null)?"<null>":this.filterDenominator));
        sb.append(',');
        sb.append("filterNumerator");
        sb.append('=');
        sb.append(((this.filterNumerator == null)?"<null>":this.filterNumerator));
        sb.append(',');
        if (sb.charAt((sb.length()- 1)) == ',') {
            sb.setCharAt((sb.length()- 1), ']');
        } else {
            sb.append(']');
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.filterDenominator == null)? 0 :this.filterDenominator.hashCode()));
        result = ((result* 31)+((this.filterNumerator == null)? 0 :this.filterNumerator.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof ConfigurationDigitalFilter) == false) {
            return false;
        }
        ConfigurationDigitalFilter rhs = ((ConfigurationDigitalFilter) other);
        return (((this.filterDenominator == rhs.filterDenominator)||((this.filterDenominator!= null)&&this.filterDenominator.equals(rhs.filterDenominator)))&&((this.filterNumerator == rhs.filterNumerator)||((this.filterNumerator!= null)&&this.filterNumerator.equals(rhs.filterNumerator))));
    }

}
