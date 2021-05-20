package HttpGw;

import PDU.PacketUDP;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;


class UPDSender implements Runnable {
    private Socket socket;
    private DatagramSocket datagramSocket;
    private PacketUDP received;

    public UPDSender(Socket socket, DatagramSocket datagramSocket, PacketUDP received) throws IOException {
        this.socket = socket;
        this.datagramSocket = datagramSocket;
        this.received = received;
    }

    public void run(){

        try {
            InetAddress address = InetAddress.getByName("localhost");
            byte[] pBytes = received.toBytes();
            if (received.getTipo() == 3) {
                System.out.println("Cliente");
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                String aux = new String(received.getPayload(), StandardCharsets.UTF_8);
                out.writeBytes(aux);
                out.flush();
                socket.close();
            }
            else {
                DatagramPacket packet = new DatagramPacket(pBytes, pBytes.length,address,8880);
                datagramSocket.send(packet);
            }


        } catch (IOException e) {
            e.printStackTrace();
        }


    }
}



public class HttpGw {
    public static void main(String[] args) throws IOException {
        ServerSocket ss = new ServerSocket(8080);
        DatagramSocket datagramSocket = new DatagramSocket(8888);
        Map<InetAddress, List<String>> map = new HashMap<>();
        Map<Integer,Socket> sockets = new HashMap<>();
        Map<Integer, Boolean> sleep = new HashMap<>();
        Queue<DatagramPacket> queue = new LinkedBlockingDeque<>();
        Thread listener = new Thread(new ListenerHttpGw(datagramSocket, queue));
        listener.start();
        Thread interpretador = new Thread(new InterpretadorHttpGw(datagramSocket,map,sockets,sleep,queue));
        interpretador.start();

        while (true) {
            Socket socket = ss.accept();
            Thread worker = new Thread(new RequestHandler(socket,datagramSocket, map,sockets,sleep));
            worker.start();
        }
    }
}
