package io.micronaut.validation;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import java.util.HashMap;
import java.util.Map;

@Validated
@Controller("/api")
public class BookmarkController {

    @Get("/bookmarks{?offset,max,sort,order}")
    public HttpStatus index(@PositiveOrZero @Nullable Integer offset,
                            @Positive @Nullable Integer max,
                            @Nullable @Pattern(regexp = "name|href|title") String sort,
                            @Nullable @Pattern(regexp = "asc|desc|ASC|DESC") String order) {
        return HttpStatus.OK;
    }

    @Get("/bookmarks/list{?paginationCommand*}")
    public Map<String, Object> list(@Valid @Nullable PaginationCommand paginationCommand) {
        Map<String, Object> m = new HashMap<>();
        if (paginationCommand.getIds() != null) {
            m.put("ids", paginationCommand.getIds().stream()
                    .map(String::valueOf)
                    .reduce((a, b) -> a + ", " + b)
                    .get());
        }
        if (paginationCommand.getOffset() != null) {
            m.put("offset", paginationCommand.getOffset());
        }
        if (paginationCommand.getMax() != null) {
            m.put("max", paginationCommand.getMax());
        }
        if (paginationCommand.getOrder() != null) {
            m.put("order", paginationCommand.getOrder());
        }
        if (paginationCommand.getSort() != null) {
            m.put("sort", paginationCommand.getSort());
        }
        if (paginationCommand.getColumns() != null) {
            m.put("columns", paginationCommand.getColumns()
                    .stream()
                    .reduce((a, b) -> a + ", " + b)
                    .get());
        }
        return m;
    }

}
