// TODO: some message when the server is completely closed
// TODO: gracefully shutdown the server

package com.kerrishaus.StrategyServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONObject;

public class StrategyServer extends WebSocketServer
{
    public static void main(String[] args)
    {
        String host = "localhost";
        int port = 27002;

        System.out.println("Starting Strategy game server...");

        StrategyServer server = new StrategyServer(new InetSocketAddress(host, port));

        server.setConnectionLostTimeout(3);
        server.start();
    }

    // these ids are recycled when the server is empty
    int lifetimeClients = 1;

    public HashMap<String, Client> clients = new HashMap<>();
    public HashMap<String, Lobby> lobbies = new HashMap<>();

    public StrategyServer(InetSocketAddress address)
    {
        super(address);
    }

    @Override
    public void onStart()
    {
        Runtime.getRuntime().addShutdownHook(new ShutdownThread(this));

        System.out.println("Server started on " + this.getPort() + " successfully.");
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake)
    {
        final Client newClient = new Client(conn, lifetimeClients++);

        this.clients.put(conn.getRemoteSocketAddress().toString(), newClient);

        System.out.println("New connection established: " + conn.getRemoteSocketAddress());

        final JSONObject response = new JSONObject();
        response.put("command", "welcome");
        response.put("clientId", newClient.id);
        conn.send(response.toString());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote)
    {
        final String clientAddress = conn.getRemoteSocketAddress().toString();

        final Client client = clients.get(clientAddress);

        if (this.lobbies.containsKey(client.lobbyId))
            this.lobbies.get(client.lobbyId).removeClient(client);

        clients.remove(conn.getRemoteSocketAddress().toString());

        // recycle client ids
        if (this.clients.size() <= 0)
            // starts at 1 because local clients are always 0
            this.lifetimeClients = 1;

        System.out.println("closed " + conn.getRemoteSocketAddress() + " with exit code " + code + " additional info: " + reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message)
    {
        final String clientAddress = conn.getRemoteSocketAddress().toString();

        // TODO: check to make sure the client is in the list?
        final Client client = clients.get(clientAddress);

        System.out.println(conn.getRemoteSocketAddress() + " (client " + client.id + ") > " + message);

        final JSONObject command = new JSONObject(message);

        final String commandString = command.getString("command");

        // always overwrite whatever the client says their id is
        // and put what the server thinks their id is
        // in an effort to prevent fake commands
        command.put("clientId", client.id);

        if (commandString.equals("joinLobbyRequest"))
        {
            final String lobbyId = command.getString("lobbyId");

            if (!lobbies.containsKey(lobbyId))
            {
                final JSONObject response = new JSONObject();
                response.put("command", "invalidLobbyId");
                response.put("lobbyId", lobbyId);
                conn.send(response.toString());

                System.out.println("Client requested to join lobby with ID " + lobbyId + " which does not exist.");
            }
            else
            {
                final Lobby lobby = lobbies.get(lobbyId);

                final JSONObject response = new JSONObject();
                response.put("command", "joinLobbyRequest");
                response.put("requesterId", client.id);
                clients.get(lobby.owner.connection.getRemoteSocketAddress().toString()).connection.send(response.toString());
            }
        }
        else if (commandString.equals("joinLobbyAccept"))
        {
            final Lobby lobby = lobbies.get(client.lobbyId);

            if (client.id != lobby.ownerId)
            {
                System.out.println("ERROR: Client " + client.id + " sent a joinLobby accept/deny packet, but " + lobby.ownerId + " is the owner!");
                return;
            }

            final int requesterId = command.getInt("requesterId");

            // TODO: improve this, there should be a method to get client by their id
            this.clients.forEach((address, requester) ->
            {
                if (requester.id == requesterId)
                {
                    requester.name  = command.getString("name");
                    requester.type  = command.getString("type");
                    requester.color = command.getString("color");

                    lobby.addClient(requester);
                    requester.lobbyId = lobby.id;

                    final JSONObject response = new JSONObject();
                    response.put("command", "joinLobbyAccept");
                    response.put("lobbyId", lobby.id);
                    response.put("ownerId", lobby.ownerId);
                    response.put("clients", lobby.getClients());
                    requester.connection.send(response.toString());

                    System.out.println("Accepted client with packet: " + response.toString());

                    return;
                }
            });
        }
        else if (commandString.equals("joinLobbyDeny"))
        {
            final Lobby lobby = lobbies.get(client.lobbyId);

            if (client.id != lobby.ownerId)
            {
                System.out.println("ERROR: Someone sent a joinLobby accept/deny packet, but it was not the lobby owner!");
                return;
            }

            final int requesterId = command.getInt("requesterId");

            // TODO: improve this, there should be a method to get client by their id
            this.clients.forEach((address, requester) ->
             {
                 if (requester.id == requesterId)
                 {
                     lobby.addClient(requester);
                     requester.lobbyId = lobby.id;

                     final JSONObject response = new JSONObject();
                     response.put("command", "joinLobbyDeny");
                     response.put("lobbyId", lobby.id);
                     requester.connection.send(response.toString());

                     return;
                 }
             });
        }
        else if (commandString.equals("createLobby"))
        {
            final String lobbyId = command.getString("lobbyId");

            if (lobbies.containsKey(lobbyId))
            {
                final JSONObject response = new JSONObject();
                response.put("command", "invalidLobbyId");
                response.put("lobbyId", lobbyId);
                response.put("lobbyAlreadyExists", true);
                conn.send(response.toString());

                System.out.println("Client requested to create lobby with ID " + lobbyId + " which already exists.");

                return;
            }

            client.name  = command.getString("name");
            client.type  = command.getString("type");
            client.color = command.getString("color");

            Lobby newLobby = new Lobby(this, lobbyId, client);
            newLobby.addClient(client);
            lobbies.put(lobbyId, newLobby);

            final JSONObject response = new JSONObject();
            response.put("command", "joinLobbyAccept");
            response.put("lobbyId", lobbyId);
            response.put("ownerId", client.id);
            response.put("clients", newLobby.getClients());
            conn.send(response.toString());
        }
        else if (commandString.equals("startGame"))
            this.lobbies.get(client.lobbyId).startGame(client.id, command);
        else if (commandString.equals("worldData"))
            this.lobbies.get(client.lobbyId).worldData(client.id, command);
        else
        {
            // rebroadcast the command and don't do anything special
            this.broadcast(command.toString());
        }
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message)
    {
        System.out.println(conn.getRemoteSocketAddress() + " sent a byte buffer of size: " + message.limit());
    }

    @Override
    public void onError(WebSocket conn, Exception ex)
    {
        System.err.println("ERROR: " + conn.getRemoteSocketAddress()  + ":" + ex);
    }

    public void closeLobby(final String lobbyId)
    {
        this.lobbies.remove(lobbyId);
    }

    public void kickClient(final Client client, final String reason)
    {
        // TODO: remove the client from a lobby if they are in one

        this.broadcast("clientLeft;" + client.id + ";" + reason);

        client.connection.close();

        // client is removed from StrategyServer#clients by the StrategyServer#onClose method.
    }
}