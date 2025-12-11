package com.example.lidarcbackend.model.entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@NoArgsConstructor
@Getter
@Setter
public class ComparisonFilePK implements Serializable {
    private Long comparisonId;
    private Long fileId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ComparisonFilePK userPK)) return false;
        return comparisonId.equals(userPK.comparisonId) && fileId.equals(userPK.fileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(comparisonId, fileId);
    }
}
