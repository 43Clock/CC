package FastFileServer;

import PDU.PacketUDP;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Queue;

public class InterpretadorFFS implements Runnable {
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
