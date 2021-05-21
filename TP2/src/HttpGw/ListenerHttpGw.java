package HttpGw;

import PDU.PacketUDP;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;

public class ListenerHttpGw implements Runnable {
    private DatagramSocket datagramSocket;
    private Queue<DatagramPacket> queue;
    private ReentrantLock lock;

    public ListenerHttpGw(DatagramSocket datagramSocket, Queue<DatagramPacket> queue, ReentrantLock lock) {
        this.datagramSocket = datagramSocket;
        this.queue = queue;
        this.lock = lock;
    }

    public void run() {
        try {
            while (true) {
                byte[] pBytes_received = new byte[PacketUDP.MAX_SIZE+200];
                DatagramPacket packet = new DatagramPacket(pBytes_received, pBytes_received.length);
                datagramSocket.receive(packet);
                lock.lock();
                queue.add(packet);
                lock.unlock();
            }
        } catch (IOException e) {
            System.out.println("Erro: "+e.getMessage());
        }
    }
}