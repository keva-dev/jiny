package com.tuhuynh.httpserver.core.bio;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import com.tuhuynh.httpserver.core.RequestBinderBase;
import com.tuhuynh.httpserver.core.RequestUtils.RequestMethod;

import lombok.val;
import lombok.var;

public final class RequestBinderBIO extends RequestBinderBase {
    private final ArrayList<RequestHandlerBIO> middlewares;
    private final ArrayList<BaseHandlerMetadata<RequestHandlerBIO>> handlerMetadata;

    public RequestBinderBIO(RequestContext requestContext,
                            final ArrayList<RequestHandlerBIO> middlewares,
                            final ArrayList<BaseHandlerMetadata<RequestHandlerBIO>> handlerMetadata) {
        super(requestContext);
        this.middlewares = middlewares;
        this.handlerMetadata = handlerMetadata;
    }

    public HttpResponse getResponseObject() throws IOException {
        for (var h : handlerMetadata) {
            val binder = binderInit(h);

            if ((requestContext.getMethod() == h.getMethod() || (h.getMethod() == RequestMethod.ALL))
                && (binder.getRequestPath().equals(binder.getHandlerPath()) || binder
                    .isRequestWithHandlerParamsMatched())) {
                try {
                    // Handle middleware function chain
                    middlewares.addAll(Arrays.asList(h.handlers));
                    for (int i = 0; i < middlewares.size(); i++) {
                        val isLastItem = i == middlewares.size()- 1;
                        val resultFromPreviousHandler = middlewares.get(i).handleFunc(requestContext);
                        if (!isLastItem && !resultFromPreviousHandler.isAllowNext()) {
                            return resultFromPreviousHandler;
                        } else {
                            if (isLastItem) {
                                return resultFromPreviousHandler;
                            } else {
                                continue;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    return HttpResponse.of("Internal Server Error").status(500);
                }
            }
        }

        return HttpResponse.of("Not found").status(404);
    }
}
