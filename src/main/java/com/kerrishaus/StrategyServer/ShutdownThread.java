package com.kerrishaus.StrategyServer;

import org.java_websocket.server.WebSocketServer;

public class ShutdownThread extends Thread
{
    public WebSocketServer server;

    public ShutdownThread(WebSocketServer server)
    {
        this.server = server;
    }

    public void run()
    {
        System.out.println("Shutting down server...");

        try
        {
            this.server.stop();

            System.out.println("Server closed.");
        }
        catch (InterruptedException e)
        {
            System.out.println("There was an exception while trying to gracefully shut down the server: " + e.getMessage());
        }
    }
}
