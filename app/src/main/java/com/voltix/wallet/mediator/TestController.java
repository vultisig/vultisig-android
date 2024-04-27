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
//@RequestMapping(path = "/user")
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
//        Logger.i(JSON.toJSONString(map));
        return map;
    }

//    @CrossOrigin(
//        methods = {RequestMethod.POST, RequestMethod.GET}
//    )
//    @RequestMapping(
//        path = "/get/{userId}",
//        method = {RequestMethod.PUT, RequestMethod.POST, RequestMethod.GET},
//        produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
//    String info(@PathVariable(name = "userId") String userId, HttpRequest request) {
//        return userId;
//    }
//
//    @PutMapping(path = "/get/{userId}", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
//    String modify(@PathVariable("userId") String userId, @RequestParam(name = "sex") String sex,
//                  @RequestParam(name = "age") int age) {
//        String message = "The userId is %1$s, and the sex is %2$s, and the age is %3$d.";
//        return String.format(Locale.US, message, userId, sex, age);
//    }
//
//    @PostMapping(path = "/login", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
//    String login(HttpRequest request, HttpResponse response, @RequestParam(name = "account") String account,
//                 @RequestParam(name = "password") String password) {
//        Session session = request.getValidSession();
//        session.setAttribute(LoginInterceptor.LOGIN_ATTRIBUTE, true);
//
//        Cookie cookie = new Cookie("account", account + "=" + password);
//        response.addCookie(cookie);
//        return "Login successful.";
//    }

//    @Addition(stringType = "login", booleanType = true)
//    @GetMapping(path = "/userInfo", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
//    UserInfo userInfo(@CookieValue("account") String account) {
//        Logger.i("Account: " + account);
//        UserInfo userInfo = new UserInfo();
//        userInfo.setUserId("123");
//        userInfo.setUserName("AndServer");
//        return userInfo;
//    }

//    @PostMapping(path = "/upload", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
//    String upload(@RequestParam(name = "avatar") final MultipartFile file) {
//        final File localFile = FileUtils.createRandomFile(file);
//
//        // We use a sub-thread to process files so that the api '/upload' can respond faster
//        Executors.getInstance().submit(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    file.transferTo(localFile);
//
//                    // Do something ...
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        });
//
//        return localFile.getAbsolutePath();
//    }
//
//    @GetMapping(path = "/consume", consumes = {"application/json", "!application/xml"})
//    String consume() {
//        return "Consume is successful";
//    }
//
//    @GetMapping(path = "/produce", produces = {"application/json; charset=utf-8"})
//    String produce() {
//        return "Produce is successful";
//    }
//
//    @GetMapping(path = "/include", params = {"name=123"})
//    String include(@RequestParam(name = "name") String name) {
//        return name;
//    }
//
//    @GetMapping(path = "/exclude", params = "name!=123")
//    String exclude() {
//        return "Exclude is successful.";
//    }
//
//    @GetMapping(path = {"/mustKey", "/getName"}, params = "name")
//    String getMustKey(@RequestParam(name = "name") String name) {
//        return name;
//    }
//
//    @PostMapping(path = {"/mustKey", "/postName"}, params = "name")
//    String postMustKey(@RequestParam(name = "name") String name) {
//        return name;
//    }
//
//    @GetMapping(path = "/noName", params = "!name")
//    String noName() {
//        return "NoName is successful.";
//    }
//
//    @PostMapping(path = "/formPart")
//    UserInfo forPart(
//            @QueryParam
//            @FormPart(name = "user") UserInfo userInfo) {
//        return userInfo;
//    }
//
//    @PostMapping(path = "/jsonBody")
//    UserInfo jsonBody(@RequestBody UserInfo userInfo) {
//        return userInfo;
//    }
//
//    @PostMapping(path = "/listBody")
//    List<UserInfo> jsonBody(@RequestBody List<UserInfo> infoList) {
//        return infoList;
//    }
}