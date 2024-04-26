package com.voltix.wallet.mediator

import spark.Spark.*
class Server {
    private val port = 18080
    fun StartMediator() {
        port(port)
        get("/hello") { req, res -> "Hello World" }
    }
    fun stopServer() {
        stop()
    }
}