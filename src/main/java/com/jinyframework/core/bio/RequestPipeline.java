package com.jinyframework.core.bio;

import com.jinyframework.core.RequestBinderBase.Handler;
import com.jinyframework.core.RequestBinderBase.HandlerMetadata;
import com.jinyframework.core.RequestBinderBase.RequestTransformer;
import com.jinyframework.core.RequestPipelineBase;
import com.jinyframework.core.utils.ParserUtils;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

@Slf4j
@RequiredArgsConstructor
public final class RequestPipeline implements RequestPipelineBase, Runnable {
    private final Socket socket;
    private final List<HandlerMetadata<Handler>> middlewares;
    private final List<HandlerMetadata<Handler>> handlers;
    private final RequestTransformer transformer;
    private BufferedReader in;
    private PrintWriter out;

    @SneakyThrows
    @Override
    public void run() {
        socket.setSoTimeout(5000);

        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), false);

        while (!socket.isClosed()) { // TODO: Keep-Alive check
            process();
        }

        in.close();
        out.close();
        socket.close();
    }

    public void process() throws IOException {
        val requestStringArr = new ArrayList<String>();
        var inputLine = "";
        var isFirstLine = true;
        var method = "";
        var contentLength = 0;
        try {
            while (!socket.isClosed()) {
                inputLine = in.readLine();
                if (inputLine == null || inputLine.isEmpty()) {
                    break;
                }
                if (isFirstLine) {
                    isFirstLine = false;
                    method = new StringTokenizer(inputLine).nextToken().toLowerCase();
                }
                if (inputLine.toLowerCase().startsWith("content-length: ")) {
                    val contentLengthStr = inputLine.toLowerCase().replace("content-length: ", "");
                    contentLength = Integer.parseInt(contentLengthStr);
                }
                requestStringArr.add(inputLine);
            }

            if (requestStringArr.size() == 0) {
                return;
            }

            val body = new StringBuilder();
            if (!method.equals("get") && contentLength > 0) {
                while (in.ready()) {
                    body.append((char) in.read());
                }
            }

            val requestContext = ParserUtils
                    .parseRequest(requestStringArr.toArray(new String[0]), body.toString());

            val responseObject = new RequestBinder(requestContext, middlewares, handlers).getResponseObject();
            val responseString = ParserUtils.parseResponse(responseObject, transformer);

            out.write(responseString);
            out.flush();
        } catch (SocketException | SocketTimeoutException ignored) {
            socket.close();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}
