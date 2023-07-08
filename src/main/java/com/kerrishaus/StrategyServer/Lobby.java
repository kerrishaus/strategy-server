package com.kerrishaus.StrategyServer;

import org.json.JSONObject;

import java.util.*;

public class Lobby
{
    StrategyServer server;

    public String id;
    public int    ownerId;

    public Map<Integer, Client> clients = new HashMap<>();

    public ArrayList<Integer> clientTurnOrder = new ArrayList<>();

    public Lobby(StrategyServer server, String id, int ownerId)
    {
        this.server  = server;
        this.id      = id;
        this.ownerId = ownerId;
    }

    public void broadcast(final String string)
    {
        this.clients.forEach((clientId, client) -> {
            client.connection.send(string);
        });
    }

    public void startGame(final int fromClientId, final JSONObject command)
    {
        if (fromClientId != this.ownerId)
        {
            System.out.println(fromClientId + " requested to start the game, but they are not the owner. Ignoring.");
            return;
        }

        if (this.clients.size() < 2)
        {
            System.out.println(fromClientId + " requested to start the game, but there are not enough players. Ignoring.");
            return;
        }

        this.broadcast(command.toString());

        System.out.println("Started game in lobby " + this.id);
    }

    public void worldData(final int clientId, final JSONObject data)
    {
        if (clientId != this.ownerId)
        {
            System.out.println("Received world data from " + clientId + " but they are not the host, ignoring.");
            return;
        }

        this.broadcast(data.toString());
    }

    public void addClient(Client client)
    {
        this.clients.put(client.id, client);
        client.lobbyId = this.id;

        this.clientTurnOrder.add(client.id);

        System.out.println("Added client " + client.id + " to lobby " + this.id + ".");

        final JSONObject command = new JSONObject();
        command.put("command", "clientJoin");
        command.put("clientId", client.id);

        this.broadcast(command.toString());
    }

    public void removeClient(Client client)
    {
        this.clients.remove(client.id);
        client.lobbyId = null;

        System.out.println("Removed client " + client.id + " from lobby " + this.id + ". There are now " + this.clients.size() + " clients in this lobby.");

        final JSONObject command = new JSONObject();
        command.put("command", "clientLeave");
        command.put("clientId", client.id);

        this.broadcast(command.toString());

        if (this.clients.size() <= 0)
        {
            System.out.println("Closing lobby " + this.id + " because it is empty.");
            this.server.closeLobby(this.id);
        }
    }
}
