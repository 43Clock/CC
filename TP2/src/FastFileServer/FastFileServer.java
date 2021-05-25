package FastFileServer;

import PDU.PacketUDP;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

class ShutDownFFS extends Thread implements Runnable{
    DatagramSocket socket;
    InetAddress ip;

    public ShutDownFFS(DatagramSocket socket,InetAddress ip) {
        this.socket = socket;
        this.ip = ip;
    }

    public void run(){
        try {
            PacketUDP packet = new PacketUDP(0,7,1,1,new byte[0]);
            socket.send(new DatagramPacket(packet.toBytes(),packet.toBytes().length,ip,8888));
            System.out.println("Closing");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

public class FastFileServer {
    public static void main(String[] args) throws IOException {
        DatagramSocket socket = new DatagramSocket(8888);
        Queue<DatagramPacket> queue = new LinkedBlockingQueue<>();
        Queue<DatagramPacket> ackQueue = new LinkedBlockingQueue<>();
        PacketUDP first = new PacketUDP(0,1,1,1,new byte[0]);
        DatagramPacket packet = new DatagramPacket(first.toBytes(),first.toBytes().length,InetAddress.getByName(args[0]),Integer.parseInt(args[1]));
        socket.send(packet);
        ReentrantLock lockQueue = new ReentrantLock();
        Thread listener = new Thread(new ListenerFFS(socket, queue,lockQueue));
        listener.start();
        Thread interpretador = new Thread(new InterpretadorFFS(socket,queue,ackQueue,InetAddress.getByName(args[0]),Integer.parseInt(args[1]),lockQueue));
        interpretador.start();
        Runtime.getRuntime().addShutdownHook(new ShutDownFFS(socket,InetAddress.getByName(args[0])));
    }
}