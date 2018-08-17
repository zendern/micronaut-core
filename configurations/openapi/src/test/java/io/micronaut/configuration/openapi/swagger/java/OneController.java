package io.micronaut.configuration.openapi.swagger.java;

import io.micronaut.http.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@Controller(value = "/one", produces = {"text/json"}, consumes = {"application/json"})
@Produces({"text/html", "text/xml"}) //Produces annotation is preferred over controller value
@Tag(name = "one")
@Tag(name = "controller")
public class OneController {


    @Get
    void get() {}

    @Get("/{uriArg}")
    @Consumes({"text/html"}) //used over the class annotations
    void get(String uriArg, String paramArg) {}

    @Put
    void put() {}

    @Post
    void post() {}

    @Delete
    void delete() {}


}
