import PDU.PacketUDP;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;

class ServerWorker implements Runnable {
    private DatagramSocket socket;
    private byte[] buf = new byte[256];

    public ServerWorker(DatagramSocket socket) {
        this.socket = socket;
    }

    private PacketUDP readPacket(DatagramPacket packet) throws UnknownHostException {

        byte[] packetData = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), 0, packetData, 0, packet.getLength());
        PacketUDP received = new PacketUDP(packetData);
        String payload = new String(received.getPayload(), StandardCharsets.UTF_8);
        System.out.println("Received: " + payload);
        System.out.println(received);
        payload = payload + "/FFS";
        return new PacketUDP(received.getIdent_Pedido(),3,received.getChunk(),received.getFragmento(),received.getIp(),payload.getBytes(StandardCharsets.UTF_8));
    }

    public void run(){

        while (true){
            DatagramPacket packet = new DatagramPacket(buf,buf.length);
            try {
                socket.receive(packet);
                InetAddress address = packet.getAddress();
                int port = packet.getPort();
                PacketUDP returner = readPacket(packet);
                byte[] returner_bytes = returner.toBytes();
                packet = new DatagramPacket(returner_bytes, returner_bytes.length,address,8888);
                socket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
}

public class FastFileServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        DatagramSocket socket = new DatagramSocket(8880);
        byte[] b = new byte[]{5};
        PacketUDP first = new PacketUDP(1,1,1,1,socket.getInetAddress(),b);
        socket.send(new DatagramPacket(first.toBytes(),first.toBytes().length,InetAddress.getByName("localhost"),8888));
        Thread worker = new Thread(new ServerWorker(socket));
        worker.start();
        worker.join();
    }
}