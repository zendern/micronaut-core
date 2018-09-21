package io.micronaut.validation;

import javax.annotation.Nullable;
import java.util.List;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;

public class PaginationCommand {
    @PositiveOrZero
    @Nullable
    private Integer offset;

    @Positive
    @Nullable
    private Integer max;

    @Nullable
    @Pattern(regexp = "name|href|title")
    private String sort;

    @Nullable
    @Pattern(regexp = "asc|desc|ASC|DESC")
    private String order;

    @Nullable
    private List<String> columns;

    @Nullable
    private List<Integer> ids;

    @Nullable
    public Integer getOffset() {
        return offset;
    }

    public void setOffset(@Nullable Integer offset) {
        this.offset = offset;
    }

    @Nullable
    public Integer getMax() {
        return max;
    }

    public void setMax(@Nullable Integer max) {
        this.max = max;
    }

    @Nullable
    public String getSort() {
        return sort;
    }

    public void setSort(@Nullable String sort) {
        this.sort = sort;
    }

    @Nullable
    public String getOrder() {
        return order;
    }

    public void setOrder(@Nullable String order) {
        this.order = order;
    }

    @Nullable
    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(@Nullable List<String> columns) {
        this.columns = columns;
    }

    @Nullable
    public List<Integer> getIds() {
        return ids;
    }

    public void setIds(@Nullable List<Integer> ids) {
        this.ids = ids;
    }
}
