package gg.modl.minecraft.api.http.response;

import lombok.Getter;

import java.util.List;

@Getter
public abstract class PaginatedResponse<T> {
    private int totalCount;
    private int page;
    private boolean hasMore;
    private int status;

    protected PaginatedResponse() {
    }

    protected PaginatedResponse(int totalCount, int page, boolean hasMore, int status) {
        this.totalCount = totalCount;
        this.page = page;
        this.hasMore = hasMore;
        this.status = status;
    }

    public abstract List<T> getItems();
}
