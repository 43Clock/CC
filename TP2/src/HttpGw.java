import PDU.PacketUDP;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

class ProcessRequest implements Runnable {
    private Socket socket;
    private DatagramSocket datagramSocket;
    private Map<String,String> map;
    private String msg;

    public ProcessRequest(Socket socket, DatagramSocket datagramSocket, Map<String, String> map, String msg) {
        this.socket = socket;
        this.datagramSocket = datagramSocket;
        this.map = map;
        this.msg = msg;
    }

    void sendMessageOut(String response) throws IOException {
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        out.writeUTF(response);
        out.flush();
    }

    @Override
    public void run() {
        try {
            InetAddress address = InetAddress.getByName("localhost");
            PacketUDP p = new PacketUDP(1, 1, 1, 1, msg.getBytes(StandardCharsets.UTF_8));
            byte[] pBytes = p.toBytes();
            DatagramPacket packet = new DatagramPacket(pBytes, pBytes.length,address,4445);
            datagramSocket.send(packet);
            byte[] pBytes_received = new byte[2048*2];
            DatagramPacket packet2 = new DatagramPacket(pBytes_received, pBytes_received.length);
            datagramSocket.receive(packet2);
            byte[] result = new byte[packet2.getLength()];
            System.arraycopy(packet2.getData(),0,result,0,packet2.getLength());
            PacketUDP received = new PacketUDP(result);
            System.out.println(received);
            datagramSocket.close();
            sendMessageOut(new String(received.getPayload(),StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}

class RequestHandler implements Runnable {
    private Socket socket;
    private Map<String,String> map;
    private DataOutputStream out;
    private DataInputStream in;

    public RequestHandler(Socket socket, Map<String, String> map) {
        this.socket = socket;
        this.map = map;
    }


    public void run() {
        try {
            this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            DatagramSocket datagramSocket = new DatagramSocket(8080);
            String msg = in.readUTF();
            Thread worker = new Thread(new ProcessRequest(socket,datagramSocket, map,msg));
            worker.start();

        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
}



public class HttpGw {
    public static void main(String[] args) throws IOException {
        ServerSocket ss = new ServerSocket(8080);
        Map<String, String> map = new HashMap<>();

        while (true) {
            Socket socket = ss.accept();
            Thread worker = new Thread(new RequestHandler(socket, map));
            worker.start();
        }
    }
}
