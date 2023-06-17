package com.kerrishaus.StrategyServer;

import org.java_websocket.WebSocket;

public class Client
{
    public WebSocket connection;
    public int id;

    public Client(WebSocket connection, int id)
    {
        this.connection = connection;
        this.id = id;
    }
}
