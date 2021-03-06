package org.hisp.dhis.response.object;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Setter
@ToString
public class ErrorReport
{
    @JsonProperty
    private String message;

    @JsonProperty
    private String mainKlass;

    @JsonProperty
    private String errorCode;

    @JsonProperty
    private String mainId;

    @JsonProperty
    private String errorProperty;
}
