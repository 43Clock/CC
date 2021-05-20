package FastFileServer;

import PDU.PacketUDP;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Queue;

public class ListenerFFS implements Runnable {
    private DatagramSocket datagramSocket;
    private Queue<DatagramPacket> queue;

    public ListenerFFS(DatagramSocket datagramSocket, Queue<DatagramPacket> queue) {
        this.datagramSocket = datagramSocket;
        this.queue = queue;
    }

    public void run(){
        try {
            while (true) {
                byte[] pBytes_received = new byte[PacketUDP.MAX_SIZE+200];
                DatagramPacket packet = new DatagramPacket(pBytes_received, pBytes_received.length);
                datagramSocket.receive(packet);
                queue.add(packet);
            }
        } catch (IOException e) {
            System.out.println("Erro: "+e.getMessage());
        }
    }
}
