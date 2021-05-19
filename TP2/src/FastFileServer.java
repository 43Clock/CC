import PDU.PacketUDP;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

class InterpretadorFFS implements Runnable {
    private DatagramSocket socket;
    private Queue<DatagramPacket> queue;

    public InterpretadorFFS(DatagramSocket socket, Queue<DatagramPacket> queue) {
        this.socket = socket;
        this.queue = queue;
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
                    res = new PacketUDP(received.getIdent_Pedido(), 3, received.getChunk(), received.getFragmento(), packet.getAddress(), ret.toString().getBytes(StandardCharsets.UTF_8));
                }else {
                    res = new PacketUDP(received.getIdent_Pedido(), 3, received.getChunk(), received.getFragmento(), packet.getAddress(), new byte[0]);
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
                res = new PacketUDP(received.getIdent_Pedido(), 5, chunks, fragmento, packet.getAddress(), send);
                break;
        }
        return res;
    }

    public void run(){

        while (true){
            try {
                while (queue.peek() == null) ;
                DatagramPacket packet = queue.remove();
                PacketUDP returner = readPacket(packet);
                byte[] returner_bytes = returner.toBytes();
                packet = new DatagramPacket(returner_bytes, returner_bytes.length,packet.getAddress(),8888);
                socket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
}

class ListenerFFS implements Runnable {
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

public class FastFileServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        DatagramSocket socket = new DatagramSocket(8880);
        Queue<DatagramPacket> queue = new LinkedBlockingQueue<>();
        byte[] b = new byte[]{5};
        PacketUDP first = new PacketUDP(0,1,1,1,socket.getInetAddress(),b);
        socket.send(new DatagramPacket(first.toBytes(),first.toBytes().length,InetAddress.getByName("localhost"),8888));
        Thread listener = new Thread(new ListenerFFS(socket, queue));
        listener.start();
        Thread interpretador = new Thread(new InterpretadorFFS(socket,queue));
        interpretador.start();
    }
}