package com.pcitech.http.ingestion.core.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record JiaduResultInfo(
        @JsonProperty("OpCode") int opCode,
        @JsonProperty("OpDesc") String opDesc
) {
    public static JiaduResultInfo success() {
        return new JiaduResultInfo(0, "成功");
    }

    public static JiaduResultInfo failure(int opCode, String description) {
        return new JiaduResultInfo(opCode, description);
    }
}
