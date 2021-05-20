package FastFileServer;

import PDU.PacketUDP;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class InterpretadorFFS implements Runnable {
    private DatagramSocket socket;
    private Queue<DatagramPacket> queue;
    private InetAddress httpgwIP;
    private int httpgwPort;

    public InterpretadorFFS(DatagramSocket socket, Queue<DatagramPacket> queue, InetAddress httpgwIP, int httpgwPort) {
        this.socket = socket;
        this.queue = queue;
        this.httpgwIP = httpgwIP;
        this.httpgwPort = httpgwPort;
    }

    private PacketUDP readPacket(DatagramPacket packet) throws IOException {
        byte[] packetData = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), 0, packetData, 0, packet.getLength());
        PacketUDP received = new PacketUDP(packetData);
        PacketUDP res = null;
        switch (received.getTipo()) {
            case 2:
                String path = new String(received.getPayload(), StandardCharsets.UTF_8);
                File file = new File("./"+path);
                if (file.exists()) {
                    long size = file.length();
                    StringBuilder ret = new StringBuilder();
                    ret.append(path).append("=").append(size);
                    res = new PacketUDP(received.getIdent_Pedido(), 3, received.getChunk(), received.getFragmento(), received.getIp(), ret.toString().getBytes(StandardCharsets.UTF_8));
                }else {
                    res = new PacketUDP(received.getIdent_Pedido(), 3, received.getChunk(), received.getFragmento(), received.getIp(), new byte[0]);
                }
                break;
            case 4:
                String path2 = new String(received.getPayload(), StandardCharsets.UTF_8);
                File file2 = new File("./"+path2);
                byte[] fileContent = Files.readAllBytes(file2.toPath());
                int chunks = received.getChunk();
                int fragmento = received.getFragmento();
                byte[] send;
                if (fragmento == chunks) {
                    send = Arrays.copyOfRange(fileContent, PacketUDP.MAX_SIZE * (chunks - 1), fileContent.length);
                }
                else{
                    send = Arrays.copyOfRange(fileContent,PacketUDP.MAX_SIZE*(fragmento-1),PacketUDP.MAX_SIZE*(fragmento));
                }
                res = new PacketUDP(received.getIdent_Pedido(), 5, chunks, fragmento, received.getIp(), send);
                break;
            case 6:
                queue.add(packet);
                break;
        }
        return res;
    }

    public void run(){

        while (true){
            try {
                while (queue.peek() == null);
                DatagramPacket packet = queue.remove();
                PacketUDP returner = readPacket(packet);
                byte[] returner_bytes = returner.toBytes();
                packet = new DatagramPacket(returner_bytes, returner_bytes.length,httpgwIP,httpgwPort);
                socket.send(packet);
                if (returner.getTipo() == 5){
                    Thread ack = new Thread(new WaitForAck(socket, queue, httpgwIP, httpgwPort, returner));
                    ack.start();
                    ack.join();
                }
                System.out.println("Packet Sent");
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }

        }
    }
}

class WaitForAck implements Runnable {
    private DatagramSocket socket;
    private Queue<DatagramPacket> queue;
    private InetAddress httpgwIP;
    private int httpgwPort;
    private PacketUDP returner;

    public WaitForAck(DatagramSocket socket, Queue<DatagramPacket> queue, InetAddress httpgwIP, int httpgwPort, PacketUDP returner) {
        this.socket = socket;
        this.queue = queue;
        this.httpgwIP = httpgwIP;
        this.httpgwPort = httpgwPort;
        this.returner = returner;
    }

    public void run(){
        try {
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            executor.schedule(new ResendPacket(socket, httpgwIP, httpgwPort, returner), 8, TimeUnit.SECONDS);
            while (queue.peek() == null);
            DatagramPacket temp;
            PacketUDP received2;
            while (true) {
                temp = queue.remove();
                byte[] result2 = new byte[temp.getLength()];
                System.arraycopy(temp.getData(), 0, result2, 0, temp.getLength());
                received2 = new PacketUDP(result2);
                if(received2.getTipo() == 6 && received2.getChunk() == returner.getChunk() && received2.getFragmento() == returner.getFragmento() && received2.getIdent_Pedido() == returner.getIdent_Pedido()){
                    break;
                }
                else{
                    queue.add(temp);
                }
            }
            executor.shutdownNow();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

    }
}

class ResendPacket implements Runnable {
    private DatagramSocket socket;
    private InetAddress httpgwIP;
    private int httpgwPort;
    private PacketUDP packet;

    public ResendPacket(DatagramSocket socket, InetAddress httpgwIP, int httpgwPort, PacketUDP packet) {
        this.socket = socket;
        this.httpgwIP = httpgwIP;
        this.httpgwPort = httpgwPort;
        this.packet = packet;
    }

    public void run() {
        try {
            byte[] returner_bytes = packet.toBytes();
            DatagramPacket packetD = new DatagramPacket(returner_bytes, returner_bytes.length,httpgwIP,httpgwPort);
            socket.send(packetD);
            System.out.println("PacketResent");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}