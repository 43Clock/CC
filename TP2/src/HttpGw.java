import PDU.PacketUDP;

import javax.xml.crypto.Data;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


class RequestHandler implements Runnable {
    private Socket socket;
    private DatagramSocket datagramSocket;
    private Map<String,String> map;
    private Map<Integer,Socket> sockets;
    private BufferedWriter out;
    private BufferedReader in;

    public RequestHandler(Socket socket, DatagramSocket datagramSocket, Map<String, String> map, Map<Integer, Socket> sockets) {
        this.socket = socket;
        this.datagramSocket = datagramSocket;
        this.map = map;
        this.sockets = sockets;
    }

    private String parseHTTP(String http) {
        String[] splited = http.split("\n");
        Pattern pattern = Pattern.compile("GET /(.+) HTTP/1\\.1");
        Matcher matcher = pattern.matcher(splited[0]);
        if (matcher.find())
            return matcher.group(1);
        else return null;
    }

    public void run() {
        try {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            //DatagramSocket datagramSocket = new DatagramSocket(8080);
            StringBuilder msg = new StringBuilder();
            String str;
            while (!(str = in.readLine()).equals("")) {
                msg.append(str).append("\n");
            }
            String file = parseHTTP(msg.toString());
            if(file != null) {
                PacketUDP p = new PacketUDP(sockets.size() + 1, 2, 1, 1, file.getBytes(StandardCharsets.UTF_8));
                sockets.put(p.getIdent_Pedido(), socket);
                Thread worker = new Thread(new UPDSender(socket,datagramSocket,p));
                worker.start();
            }

        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
}

class UDPListener implements Runnable {
    private DatagramSocket datagramSocket;
    private Map<String, String> map;
    private Map<Integer, Socket> sockets;

    public UDPListener(DatagramSocket datagramSocket, Map<String, String> map, Map<Integer, Socket> sockets) {
        this.datagramSocket = datagramSocket;
        this.map = map;
        this.sockets = sockets;
    }

    public void run() {
        try {
            while (true) {
                byte[] pBytes_received = new byte[2048 * 2];
                DatagramPacket packet = new DatagramPacket(pBytes_received, pBytes_received.length);
                datagramSocket.receive(packet);
                byte[] result = new byte[packet.getLength()];
                System.arraycopy(packet.getData(),0,result,0,packet.getLength());
                PacketUDP received = new PacketUDP(result);
                if(received.getTipo() != 1){
                    Thread sender = new Thread(new UPDSender(sockets.get(received.getIdent_Pedido()),datagramSocket, received));
                    sender.start();
                }
                else {
                    System.out.println("Adiciona IP à lista");
                }
            }


        } catch (IOException e) {
            System.out.println("Erro: "+e.getMessage());
        }

    }
}

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
        //Aqui vê qual é o tipo do pacote e faz cenas com isso

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
                System.out.println("FFS");
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
        Map<String, String> map = new HashMap<>();
        Map<Integer,Socket> sockets = new HashMap<>();
        Thread listener = new Thread(new UDPListener(datagramSocket, map,sockets));
        listener.start();

        while (true) {
            Socket socket = ss.accept();
            Thread worker = new Thread(new RequestHandler(socket,datagramSocket, map,sockets));
            worker.start();
        }
    }
}
