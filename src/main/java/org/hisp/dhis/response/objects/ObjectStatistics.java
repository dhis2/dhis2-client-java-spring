package org.hisp.dhis.response.objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ObjectStatistics
{
    @JsonProperty
    private Integer created;

    @JsonProperty
    private Integer updated;

    @JsonProperty
    private Integer deleted;

    @JsonProperty
    private Integer ignored;
}
