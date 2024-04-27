package com.voltix.wallet.mediator;

import com.yanzhenjie.andserver.annotation.GetMapping;
import com.yanzhenjie.andserver.annotation.RequestMapping;
import com.yanzhenjie.andserver.annotation.RequestMethod;
import com.yanzhenjie.andserver.annotation.RestController;
import com.yanzhenjie.andserver.http.HttpRequest;
import com.yanzhenjie.andserver.http.HttpResponse;
import com.yanzhenjie.andserver.util.MediaType;

import java.util.HashMap;
import java.util.Map;

@RestController
class TestController {
    @GetMapping("/ping")
    public String sayHello(HttpRequest request, HttpResponse response) {
        return "Voltix Router is running";
    }
    @RequestMapping(
            path = "/connection",
            method = {RequestMethod.GET},
            produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    Object getConnection(HttpRequest request) {
        Map<String, Object> map = new HashMap<>();
        map.put("getLocalAddr", request.getLocalAddr());
        map.put("getLocalName", request.getLocalName());
        map.put("getLocalPort", request.getLocalPort());
        map.put("getRemoteAddr", request.getRemoteAddr());
        map.put("getRemoteHost", request.getRemoteHost());
        map.put("getRemotePort", request.getRemotePort());
        return map;
    }


}