package io.cradesk.api.sbom;

import java.util.List;

public record ComponentPage(List<ComponentResponse> items, int page, int size, long totalItems, int totalPages) {
}
