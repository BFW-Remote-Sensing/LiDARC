package com.example.lidarcbackend.model.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@NoArgsConstructor
@Getter
@Setter
public class ComparisonFolderPK implements Serializable {
    private Long comparisonId;
    private Long folderId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ComparisonFolderPK cfPK)) return false;
        return comparisonId.equals(cfPK.comparisonId) && folderId.equals(cfPK.folderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(comparisonId, folderId);
    }
}
