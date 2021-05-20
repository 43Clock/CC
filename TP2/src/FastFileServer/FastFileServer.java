package FastFileServer;

import PDU.PacketUDP;
import sun.misc.Signal;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

class ShutDownFFS extends Thread implements Runnable{
    DatagramSocket socket;

    public ShutDownFFS(DatagramSocket socket) {
        this.socket = socket;
    }

    public void run(){
        try {
            PacketUDP packet = new PacketUDP(0,7,1,1,socket.getInetAddress(),new byte[0]);
            socket.send(new DatagramPacket(packet.toBytes(),packet.toBytes().length,socket.getInetAddress(),8888));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

public class FastFileServer {
    public static void main(String[] args) throws IOException {
        DatagramSocket socket = new DatagramSocket(8888);
        Queue<DatagramPacket> queue = new LinkedBlockingQueue<>();
        PacketUDP first = new PacketUDP(0,1,1,1,socket.getInetAddress(),new byte[0]);
        DatagramPacket packet = new DatagramPacket(first.toBytes(),first.toBytes().length,InetAddress.getByName(args[0]),Integer.parseInt(args[1]));
        socket.send(packet);
        Thread listener = new Thread(new ListenerFFS(socket, queue));
        listener.start();
        Thread interpretador = new Thread(new InterpretadorFFS(socket,queue,InetAddress.getByName(args[0]),Integer.parseInt(args[1])));
        interpretador.start();
        Runtime.getRuntime().addShutdownHook(new ShutDownFFS(socket));
    }
}