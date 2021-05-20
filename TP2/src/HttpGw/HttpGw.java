package HttpGw;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;


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
