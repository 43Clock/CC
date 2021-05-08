import java.io.*;
import java.net.Socket;

public class Cliente {
    public static void main(String[] args) throws IOException {
        Socket s = new Socket("localhost", 8080);
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
        DataInputStream in = new DataInputStream(s.getInputStream());

        BufferedReader systemIn = new BufferedReader(new InputStreamReader(System.in));

        String userInput = systemIn.readLine();
        out.writeUTF(userInput);
        out.flush();
        System.out.println(in.readUTF());



        /*try {
            DatagramSocket socket = new DatagramSocket();
            InetAddress address = InetAddress.getByName("localhost");
            String msg = "Hello";
            byte[] buff;
            buff = msg.getBytes(StandardCharsets.UTF_8);
            Packet p = new Packet(1, 1, 1, 1, buff);
            byte[] pBytes = p.toBytes();
            DatagramPacket packet = new DatagramPacket(pBytes, pBytes.length,address,4445);
            socket.send(packet);
            byte[] pBytes_received = new byte[2048*2];
            DatagramPacket packet2 = new DatagramPacket(pBytes_received, pBytes_received.length);
            socket.receive(packet2);
            Packet received = new Packet(packet2.getData());
            System.out.println(received);
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }*/
    }
}