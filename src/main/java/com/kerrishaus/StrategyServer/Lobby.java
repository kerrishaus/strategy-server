package com.kerrishaus.StrategyServer;

import org.java_websocket.server.WebSocketServer;
import org.json.JSONObject;

import java.util.HashMap;

public class Lobby
{
    WebSocketServer server;

    public String id;

    public HashMap<Integer, Client> clients = new HashMap<>();

    public Lobby(WebSocketServer server, String id)
    {
        this.server = server;
        this.id     = id;
    }

    public boolean addClient(Client client)
    {
        this.clients.put(client.id, client);

        System.out.println("Added client " + client.id + " to lobby " + this.id + ".");

        final JSONObject command = new JSONObject();
        command.put("command", "newClient");
        command.put("clientId", client.id);

        server.broadcast(command.toString());

        return true;
    }
}
