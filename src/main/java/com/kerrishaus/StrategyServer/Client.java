package com.kerrishaus.StrategyServer;

import org.java_websocket.WebSocket;

public class Client
{
    public int id;

    public WebSocket connection;

    public String lobbyId = null;

    public Client(WebSocket connection, int id)
    {
        this.connection = connection;
        this.id = id;
    }
}
