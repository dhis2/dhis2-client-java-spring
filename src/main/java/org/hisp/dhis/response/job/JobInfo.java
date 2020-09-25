package org.hisp.dhis.response.job;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobInfo
{
    @JsonProperty
    private String id;

    @JsonProperty
    private String name;

    @JsonProperty
    private JobCategory jobType;

    @JsonProperty
    private String relativeNotifierEndpoint;

    public JobInfo()
    {
    }
}
