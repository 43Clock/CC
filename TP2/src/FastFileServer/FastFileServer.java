package FastFileServer;

import PDU.PacketUDP;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;


public class FastFileServer {
    public static void main(String[] args) throws IOException {
        DatagramSocket socket = new DatagramSocket(8880);
        Queue<DatagramPacket> queue = new LinkedBlockingQueue<>();
        PacketUDP first = new PacketUDP(0,1,1,1,socket.getInetAddress(),new byte[0]);
        socket.send(new DatagramPacket(first.toBytes(),first.toBytes().length,InetAddress.getByName("localhost"),8888));
        Thread listener = new Thread(new ListenerFFS(socket, queue));
        listener.start();
        Thread interpretador = new Thread(new InterpretadorFFS(socket,queue));
        interpretador.start();
        Runtime.getRuntime().addShutdownHook(
                new Thread(new Runnable() {
                    public void run() {

                        System.out.println("Closing");
                    }
                })
        );
    }
}